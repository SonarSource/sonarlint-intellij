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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ShowAnalysisResultsCallable implements AnalysisCallback {
  private final Project project;
  private final AnalysisResultIssues analysisResultIssues;
  private final Collection<VirtualFile> affectedFiles;
  private final String whatAnalyzed;
  private final IssueManager issueManager;

  public ShowAnalysisResultsCallable(Project project, Collection<VirtualFile> affectedFiles, String whatAnalyzed) {
    this.project = project;
    this.analysisResultIssues = SonarLintUtils.get(project, AnalysisResultIssues.class);
    this.issueManager = SonarLintUtils.get(project, IssueManager.class);
    this.affectedFiles = affectedFiles;
    this.whatAnalyzed = whatAnalyzed;
  }

  @Override public void onError(Throwable e) {
    // nothing to do
  }

  @Override
  public void onSuccess(Set<VirtualFile> failedVirtualFiles) {
    Map<VirtualFile, Collection<LiveIssue>> map = affectedFiles.stream()
      .filter(f -> !failedVirtualFiles.contains(f))
      .collect(Collectors.toMap(Function.identity(), issueManager::getForFile));
    analysisResultIssues.set(map, whatAnalyzed);
    showAnalysisResultsTab();
  }

  private void showAnalysisResultsTab() {
    UIUtil.invokeLaterIfNeeded(() -> ServiceManager.getService(project, IssuesViewTabOpener.class)
      .openAnalysisResults());
  }
}
