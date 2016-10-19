/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.issue;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;
import org.sonarlint.intellij.issue.persistence.IssuePersistence;
import org.sonarlint.intellij.issue.persistence.LiveIssueCache;
import org.sonarlint.intellij.issue.tracking.Input;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.issue.tracking.Tracker;
import org.sonarlint.intellij.issue.tracking.Tracking;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.util.SonarLintUtils;

/**
 * Stores issues associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Issues can then be displayed as annotations at any time.
 */
@ThreadSafe
public class IssueManager extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(IssueManager.class);
  private final MessageBus messageBus;
  private final IssuePersistence store;
  private final LiveIssueCache cache;

  private final Lock matchingInProgress = new ReentrantLock();

  public IssueManager(Project project, LiveIssueCache cache, IssuePersistence store) {
    super(project);
    this.cache = cache;
    this.messageBus = project.getMessageBus();
    this.store = store;
  }

  public void clear() {
    cache.clear();
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
  }

  public Collection<LiveIssue> getForFile(VirtualFile file) {
    Collection<LiveIssue> issues = cache.getLive(file);
    return issues != null ? issues : Collections.emptyList();
  }

  private Collection<Trackable> getPreviousIssues(VirtualFile file) {
    Collection<LiveIssue> liveIssues = cache.getLive(file);
    if(liveIssues != null) {
      return liveIssues.stream().filter(LiveIssue::isValid).collect(Collectors.toList());
    }

    String storeKey = SonarLintUtils.getRelativePath(myProject, file);
    try {
      Collection<LocalIssueTrackable> storeIssues = store.read(storeKey);
      return storeIssues != null ? Collections.unmodifiableCollection(storeIssues) : Collections.emptyList();
    } catch (IOException e) {
      LOGGER.error(String.format("Failed to read issues from store for file %s", file.getPath()), e);
      return Collections.emptyList();
    }
  }

  private boolean wasAnalyzed(VirtualFile file) {
    if(cache.contains(file)) {
      return true;
    }
    String storeKey = SonarLintUtils.getRelativePath(myProject, file);
    return store.contains(storeKey);
  }

  public void store(Map<VirtualFile, Collection<LiveIssue>> map) {
    for (Map.Entry<VirtualFile, Collection<LiveIssue>> e : map.entrySet()) {
      store(e.getKey(), e.getValue());
    }
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(map);
  }

  void store(VirtualFile file, final Collection<LiveIssue> rawIssues) {boolean firstAnalysis = !wasAnalyzed(file);

    // this will also delete all existing issues in the file
    if (firstAnalysis) {
      // don't set creation date, as we don't know when the issue was actually created (SLI-86)
      cache.save(file, rawIssues);
    } else {
      matchWithPreviousIssues(file, rawIssues);
    }
  }

  private void matchWithPreviousIssues(VirtualFile file, Collection<LiveIssue> rawIssues) {
    matchingInProgress.lock();
    Input<Trackable> baseInput = () -> getPreviousIssues(file);
    Input<LiveIssue> rawInput = () -> rawIssues;
    updateTrackedIssues(file, baseInput, rawInput);
    matchingInProgress.unlock();
  }

  public void matchWithServerIssues(VirtualFile file, final Collection<Trackable> serverIssues) {
    matchingInProgress.lock();
    Collection<LiveIssue> previousIssues = getForFile(file);
    Input<Trackable> baseInput = () -> serverIssues;
    Input<LiveIssue> rawInput = () -> previousIssues;
    updateTrackedIssues(file, baseInput, rawInput);
    matchingInProgress.unlock();

    Map<VirtualFile, Collection<LiveIssue>> map = Collections.singletonMap(file, cache.getLive(file));
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(map);
  }

  private <T extends Trackable> void updateTrackedIssues(VirtualFile file, Input<T> baseInput, Input<LiveIssue> rawInput) {
    Collection<LiveIssue> trackedIssues = new ArrayList<>();
    Tracking<LiveIssue, T> tracking = new Tracker<LiveIssue, T>().track(rawInput, baseInput);
    for (Map.Entry<LiveIssue, ? extends Trackable> entry : tracking.getMatchedRaws().entrySet()) {
      LiveIssue rawMatched = entry.getKey();
      Trackable previousMatched = entry.getValue();
      copyFromPrevious(rawMatched, previousMatched);
      trackedIssues.add(rawMatched);
    }
    for (LiveIssue newIssue : tracking.getUnmatchedRaws()) {
      if (newIssue.getServerIssueKey() != null) {
        wipeServerIssueDetails(newIssue);
      }
      newIssue.setCreationDate(System.currentTimeMillis());
      trackedIssues.add(newIssue);
    }
    cache.save(file, trackedIssues);
  }

  private static void copyFromPrevious(LiveIssue rawMatched, Trackable previousMatched) {
    rawMatched.setCreationDate(previousMatched.getCreationDate());
    rawMatched.setServerIssueKey(previousMatched.getServerIssueKey());
    rawMatched.setResolved(previousMatched.isResolved());
    rawMatched.setAssignee(previousMatched.getAssignee());
  }

  private static void wipeServerIssueDetails(LiveIssue issue) {
    issue.setCreationDate(null);
    issue.setServerIssueKey(null);
    issue.setResolved(false);
    issue.setAssignee("");
  }
}
