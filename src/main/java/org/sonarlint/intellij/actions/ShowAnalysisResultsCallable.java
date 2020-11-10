/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.issue.IssueStore;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ShowAnalysisResultsCallable implements AnalysisCallback {
  private final Project project;
  private final Collection<VirtualFile> affectedFiles;
  private final String whatAnalyzed;

  public ShowAnalysisResultsCallable(Project project, Collection<VirtualFile> affectedFiles, String whatAnalyzed) {
    this.project = project;
    this.affectedFiles = affectedFiles;
    this.whatAnalyzed = whatAnalyzed;
  }

  @Override public void onError(Throwable e) {
    // nothing to do
  }

  @Override
  public void onSuccess(Set<VirtualFile> failedVirtualFiles) {
    IssueManager issueManager = SonarLintUtils.getService(project, IssueManager.class);
    Map<VirtualFile, Collection<LiveIssue>> map = affectedFiles.stream()
      .filter(f -> !failedVirtualFiles.contains(f))
      .collect(Collectors.toMap(Function.identity(), issueManager::getForFile));
    IssueStore issueStore = SonarLintUtils.getService(project, IssueStore.class);
    issueStore.set(map, whatAnalyzed);
    showAnalysisResultsTab();
  }

  private void showAnalysisResultsTab() {
    UIUtil.invokeLaterIfNeeded(() -> SonarLintUtils.getService(project, SonarLintToolWindow.class)
      .openAnalysisResults());
  }
}
