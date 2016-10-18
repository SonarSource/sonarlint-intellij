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
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;
import org.sonarlint.intellij.issue.persistence.IssueCache;
import org.sonarlint.intellij.issue.tracking.Input;
import org.sonarlint.intellij.issue.tracking.Tracker;
import org.sonarlint.intellij.issue.tracking.Tracking;
import org.sonarlint.intellij.messages.IssueStoreListener;

/**
 * Stores issues associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Issues can then be displayed as annotations at any time.
 */
@ThreadSafe
public class IssueManager extends AbstractProjectComponent {
  private final MessageBus messageBus;
  private IssueCache cache;

  private final Lock matchingInProgress = new ReentrantLock();

  public IssueManager(Project project, IssueCache cache) {
    super(project);
    this.cache = cache;
    this.messageBus = project.getMessageBus();
  }

  public void clear() {
    cache.clear();
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
  }

  public Collection<LocalIssuePointer> getForFile(VirtualFile file) {
    Collection<LocalIssuePointer> issues = cache.read(file);
    return issues != null ? issues : Collections.emptyList();
  }

  boolean containsFile(VirtualFile file) {
    return cache.contains(file);
  }

  public void store(Map<VirtualFile, Collection<LocalIssuePointer>> map) {
    for (Map.Entry<VirtualFile, Collection<LocalIssuePointer>> e : map.entrySet()) {
      store(e.getKey(), e.getValue());
    }
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(map);
  }

  void store(VirtualFile file, final Collection<LocalIssuePointer> rawIssues) {
    boolean firstAnalysis = !cache.contains(file);

    // this will also delete all existing issues in the file
    if (firstAnalysis) {
      // don't set creation date, as we don't know when the issue was actually created (SLI-86)
      cache.save(file, rawIssues);
    } else {
      matchWithPreviousIssues(file, rawIssues);
    }
  }

  private void matchWithPreviousIssues(VirtualFile file, Collection<LocalIssuePointer> rawIssues) {
    matchingInProgress.lock();
    Collection<LocalIssuePointer> previousIssues = getForFile(file).stream().filter(issue -> issue.isValid()).collect(Collectors.toList());
    Input<LocalIssuePointer> baseInput = () -> previousIssues;
    Input<LocalIssuePointer> rawInput = () -> rawIssues;
    updateTrackedIssues(file, baseInput, rawInput);
    matchingInProgress.unlock();
  }

  public void matchWithServerIssues(VirtualFile file, final Collection<IssuePointer> serverIssues) {
    matchingInProgress.lock();
    Collection<LocalIssuePointer> previousIssues = getForFile(file);
    Input<IssuePointer> baseInput = () -> serverIssues;
    Input<LocalIssuePointer> rawInput = () -> previousIssues;
    updateTrackedIssues(file, baseInput, rawInput);
    matchingInProgress.unlock();

    Map<VirtualFile, Collection<LocalIssuePointer>> map = Collections.singletonMap(file, cache.read(file));
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(map);
  }

  private <T extends IssuePointer> void updateTrackedIssues(VirtualFile file, Input<T> baseInput, Input<LocalIssuePointer> rawInput) {
    Collection<LocalIssuePointer> trackedIssues = new ArrayList<>();
    Tracking<LocalIssuePointer, T> tracking = new Tracker<LocalIssuePointer, T>().track(rawInput, baseInput);
    for (Map.Entry<LocalIssuePointer, ? extends IssuePointer> entry : tracking.getMatchedRaws().entrySet()) {
      LocalIssuePointer rawMatched = entry.getKey();
      IssuePointer previousMatched = entry.getValue();
      copyFromPrevious(rawMatched, previousMatched);
      trackedIssues.add(rawMatched);
    }
    for (LocalIssuePointer newIssue : tracking.getUnmatchedRaws()) {
      if (newIssue.getServerIssueKey() != null) {
        wipeServerIssueDetails(newIssue);
      }
      newIssue.setCreationDate(System.currentTimeMillis());
      trackedIssues.add(newIssue);
    }
    cache.save(file, trackedIssues);
  }

  private static void copyFromPrevious(LocalIssuePointer rawMatched, IssuePointer previousMatched) {
    rawMatched.setCreationDate(previousMatched.getCreationDate());
    rawMatched.setServerIssueKey(previousMatched.getServerIssueKey());
    rawMatched.setResolved(previousMatched.isResolved());
    rawMatched.setAssignee(previousMatched.getAssignee());
  }

  private static void wipeServerIssueDetails(LocalIssuePointer issue) {
    issue.setCreationDate(null);
    issue.setServerIssueKey(null);
    issue.setResolved(false);
    issue.setAssignee("");
  }
}
