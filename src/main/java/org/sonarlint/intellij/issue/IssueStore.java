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
import com.intellij.util.messages.MessageBusConnection;
import org.sonarlint.intellij.issue.tracking.Input;
import org.sonarlint.intellij.issue.tracking.Tracker;
import org.sonarlint.intellij.issue.tracking.Tracking;
import org.sonarlint.intellij.messages.AnalysisResultsListener;

import javax.annotation.concurrent.ThreadSafe;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores issues associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Issues can then be displayed as annotations at any time.
 */
@ThreadSafe
public class IssueStore extends AbstractProjectComponent {
  private static final long THRESHOLD = 10_000;
  private final Map<VirtualFile, Collection<IssuePointer>> storePerFile;

  public IssueStore(Project project) {
    super(project);
    this.storePerFile = new ConcurrentHashMap<>();
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(AnalysisResultsListener.SONARLINT_ANALYSIS_DONE_TOPIC, new AnalysisResultsListener() {
      @Override public void analysisDone(Map<VirtualFile, Collection<IssuePointer>> issuesPerFile) {
        for(Map.Entry<VirtualFile, Collection<IssuePointer>> e : issuesPerFile.entrySet() ) {
          if(e.getValue().isEmpty()) {
            clearFile(e.getKey());
          } else {
            store(e.getKey(), e.getValue());
          }
        }
      }
    });
  }

  public void clear() {
    storePerFile.clear();
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
    return issues == null ? Collections.<IssuePointer>emptyList() : issues;
  }

  public void clearFile(VirtualFile file) {
    storePerFile.remove(file);
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

  public void store(VirtualFile file, final Collection<IssuePointer> rawIssues) {
    // this will also delete all existing issues in the file
    final Collection<IssuePointer> previousIssues = getForFile(file);
    Collection<IssuePointer> trackedIssues = new ArrayList<>();

    Input<IssuePointer> baseInput = new Input<IssuePointer>() {
      @Override
      public Collection<IssuePointer> getIssues() {
        return previousIssues;
      }
    };
    Input<IssuePointer> rawInput = new Input<IssuePointer>() {
      @Override
      public Collection<IssuePointer> getIssues() {
        return rawIssues;
      }
    };
    Tracking<IssuePointer, IssuePointer> tracking = new Tracker<IssuePointer, IssuePointer>().track(rawInput, baseInput);
    for (Map.Entry<IssuePointer, IssuePointer> entry : tracking.getMatchedRaws().entrySet()) {
      IssuePointer rawMatched = entry.getKey();
      IssuePointer previousMatched = entry.getValue();
      rawMatched.setCreationDate(previousMatched.creationDate());
      trackedIssues.add(rawMatched);
    }
    for (IssuePointer newIssue : tracking.getUnmatchedRaws()) {
      newIssue.setCreationDate(new Date().getTime());
      trackedIssues.add(newIssue);
    }
    storePerFile.put(file, trackedIssues);
  }
}
