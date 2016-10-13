/**
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
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.messages.MessageBus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.concurrent.ThreadSafe;
import org.sonarlint.intellij.issue.tracking.Input;
import org.sonarlint.intellij.issue.tracking.Tracker;
import org.sonarlint.intellij.issue.tracking.Tracking;
import org.sonarlint.intellij.messages.IssueStoreListener;

/**
 * Stores issues associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Issues can then be displayed as annotations at any time.
 */
@ThreadSafe
public class IssueStore extends AbstractProjectComponent {
  private static final long THRESHOLD = 10_000;
  private final Map<VirtualFile, Collection<IssuePointer>> storePerFile;
  private final MessageBus messageBus;

  private final Lock matchingInProgress = new ReentrantLock();

  public IssueStore(Project project) {
    super(project);
    this.storePerFile = new ConcurrentHashMap<>();
    this.messageBus = project.getMessageBus();
  }

  public void clear() {
    storePerFile.clear();
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
  }

  private long getNumberIssues() {
    long count = 0;
    Iterator<Map.Entry<VirtualFile, Collection<IssuePointer>>> it = storePerFile.entrySet().iterator();

    while (it.hasNext()) {
      count += it.next().getValue().size();
    }

    return count;
  }

  public Map<VirtualFile, Collection<IssuePointer>> getAll() {
    return storePerFile;
  }

  @Override
  public void disposeComponent() {
    clear();
  }

  public Collection<IssuePointer> getForFile(VirtualFile file) {
    Collection<IssuePointer> issues = storePerFile.get(file);
    return issues == null ? Collections.emptyList() : issues;
  }

  public void clearFile(VirtualFile file) {
    storePerFile.remove(file);
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(Collections.singletonMap(file, Collections.emptyList()));
  }

  /**
   * Clears issues in the File if the threshold of issues per project as been passed.
   * It could be improved by being fully synchronized and deleting the oldest file closed.
   */
  public void clean(VirtualFile file) {
    long numIssues = getNumberIssues();

    if (numIssues > THRESHOLD) {
      clearFile(file);
    }
  }

  public void store(Map<VirtualFile, Collection<IssuePointer>> map) {
    for (Map.Entry<VirtualFile, Collection<IssuePointer>> e : map.entrySet()) {
      store(e.getKey(), e.getValue());
    }
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(map);
  }

  private void cleanInvalid(VirtualFile file) {
    Collection<IssuePointer> issues = storePerFile.get(file);
    if (issues == null) {
      return;
    }

    Iterator<IssuePointer> it = issues.iterator();
    while (it.hasNext()) {
      if (!it.next().isValid()) {
        it.remove();
      }
    }
  }

  void store(VirtualFile file, final Collection<IssuePointer> rawIssues) {
    boolean firstAnalysis = !storePerFile.containsKey(file);

    // clean before issue tracking
    cleanInvalid(file);

    // this will also delete all existing issues in the file
    if (firstAnalysis) {
      // don't set creation date, as we don't know when the issue was actually created (SLI-86)
      storePerFile.put(file, rawIssues);
    } else {
      matchingInProgress.lock();
      Collection<IssuePointer> previousIssues = getForFile(file);
      Collection<IssuePointer> trackedIssues = new ArrayList<>();

      Input<IssuePointer> baseInput = () -> previousIssues;
      Input<IssuePointer> rawInput = () -> rawIssues;
      updateTrackedIssues(file, trackedIssues, baseInput, rawInput);
      matchingInProgress.unlock();
    }
  }

  public void storeServerIssues(VirtualFile file, final Collection<IssuePointer> serverIssues) {
    if (!storePerFile.containsKey(file)) {
      // server issue gone, or file was renamed locally
      // TODO add support to track renamed files (or explain why not)
      return;
    }

    // clean before issue tracking
    cleanInvalid(file);

    matchingInProgress.lock();
    Collection<IssuePointer> previousIssues = getForFile(file);
    Collection<IssuePointer> trackedIssues = new ArrayList<>();

    Input<IssuePointer> baseInput = () -> serverIssues;
    Input<IssuePointer> rawInput = () -> previousIssues;
    updateTrackedIssues(file, trackedIssues, baseInput, rawInput);
    matchingInProgress.unlock();
  }

  private void updateTrackedIssues(VirtualFile file, Collection<IssuePointer> trackedIssues, Input<IssuePointer> baseInput, Input<IssuePointer> rawInput) {
    Tracking<IssuePointer, IssuePointer> tracking = new Tracker<IssuePointer, IssuePointer>().track(rawInput, baseInput);
    for (Map.Entry<IssuePointer, IssuePointer> entry : tracking.getMatchedRaws().entrySet()) {
      IssuePointer rawMatched = entry.getKey();
      IssuePointer previousMatched = entry.getValue();
      copyFromPrevious(rawMatched, previousMatched);
      trackedIssues.add(rawMatched);
    }
    for (IssuePointer newIssue : tracking.getUnmatchedRaws()) {
      if (newIssue.getServerIssueKey() != null) {
        wipeServerIssueDetails(newIssue);
      }
      newIssue.setCreationDate(System.currentTimeMillis());
      trackedIssues.add(newIssue);
    }
    storePerFile.put(file, trackedIssues);
  }

  private void copyFromPrevious(IssuePointer rawMatched, IssuePointer previousMatched) {
    rawMatched.setCreationDate(previousMatched.creationDate());
    rawMatched.setServerIssueKey(previousMatched.getServerIssueKey());
    rawMatched.setResolved(previousMatched.isResolved());
  }

  private void wipeServerIssueDetails(IssuePointer issue) {
    issue.setServerIssueKey(null);
    issue.setResolved(false);
  }
}
