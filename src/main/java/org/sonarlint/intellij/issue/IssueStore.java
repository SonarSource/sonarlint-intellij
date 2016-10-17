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
import com.intellij.util.messages.MessageBus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
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
  private final Map<VirtualFile, Collection<LocalIssuePointer>> storePerFile;
  private final Map<VirtualFile, Boolean> firstAnalysis;
  private final MessageBus messageBus;

  private final Lock matchingInProgress = new ReentrantLock();

  public IssueStore(Project project) {
    super(project);
    this.storePerFile = new ConcurrentHashMap<>();
    this.firstAnalysis = new ConcurrentHashMap<>();
    this.messageBus = project.getMessageBus();
  }

  public void clear() {
    storePerFile.clear();
    firstAnalysis.clear();
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
  }

  private long getNumberIssues() {
    long count = 0;

    for (Map.Entry<VirtualFile, Collection<LocalIssuePointer>> entry : storePerFile.entrySet()) {
      count += entry.getValue().size();
    }

    return count;
  }

  public Map<VirtualFile, Collection<LocalIssuePointer>> getAll() {
    return Collections.unmodifiableMap(storePerFile);
  }

  @Override
  public void disposeComponent() {
    clear();
  }

  public Collection<LocalIssuePointer> getForFile(VirtualFile file) {
    Collection<LocalIssuePointer> issues = storePerFile.get(file);
    return issues == null ? Collections.emptyList() : issues;
  }

  void clearFile(VirtualFile file) {
    storePerFile.remove(file);
    firstAnalysis.remove(file);
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

  public void store(Map<VirtualFile, Collection<LocalIssuePointer>> map) {
    for (Map.Entry<VirtualFile, Collection<LocalIssuePointer>> e : map.entrySet()) {
      store(e.getKey(), e.getValue());
    }
    messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(map);
  }

  private void cleanInvalid(VirtualFile file) {
    Collection<LocalIssuePointer> issues = storePerFile.get(file);
    if (issues == null) {
      return;
    }

    Iterator<LocalIssuePointer> it = issues.iterator();
    while (it.hasNext()) {
      if (!it.next().isValid()) {
        it.remove();
      }
    }
  }

  void store(VirtualFile file, final Collection<LocalIssuePointer> rawIssues) {
    boolean isFirstAnalysis = !storePerFile.containsKey(file);

    // clean before issue tracking
    cleanInvalid(file);

    // this will also delete all existing issues in the file
    if (isFirstAnalysis) {
      // don't set creation date, as we don't know when the issue was actually created (SLI-86)
      storePerFile.put(file, rawIssues);
      firstAnalysis.put(file, true);
    } else {
      matchWithPreviousIssues(file, rawIssues);
      firstAnalysis.remove(file);
    }
  }

  public boolean isFirstAnalysis(VirtualFile file) {
    return firstAnalysis.containsKey(file);
  }

  private void matchWithPreviousIssues(VirtualFile file, Collection<LocalIssuePointer> rawIssues) {
    matchingInProgress.lock();
    Collection<LocalIssuePointer> previousIssues = getForFile(file);
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

    Map<VirtualFile, Collection<LocalIssuePointer>> map = Collections.singletonMap(file, storePerFile.get(file));
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
    storePerFile.put(file, trackedIssues);
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
