/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.issue.IssueManager;

public class ShowReportCallable implements AnalysisCallback {
  private final Project project;
  private final Collection<VirtualFile> affectedFiles;
  private final String whatAnalyzed;

  public ShowReportCallable(Project project, Collection<VirtualFile> affectedFiles) {
    this(project, affectedFiles, whatAnalyzed(affectedFiles.size()));
  }

  public ShowReportCallable(Project project, Collection<VirtualFile> affectedFiles, String whatAnalyzed) {
    this.project = project;
    this.affectedFiles = affectedFiles;
    this.whatAnalyzed = whatAnalyzed;
  }

  @Override public void onError(Throwable e) {
    // nothing to do
  }

  @Override
  public void onSuccess(Set<VirtualFile> failedVirtualFiles) {
    var issueManager = SonarLintUtils.getService(project, IssueManager.class);
    var issuesPerFile = affectedFiles.stream()
      .filter(f -> !failedVirtualFiles.contains(f))
      .collect(Collectors.toMap(Function.identity(), issueManager::getForFile));
    showReportTab(new AnalysisResult(issuesPerFile, whatAnalyzed, Instant.now()));
  }

  private void showReportTab(AnalysisResult analysisResult) {
    UIUtil.invokeLaterIfNeeded(() -> SonarLintUtils.getService(project, SonarLintToolWindow.class).openReportTab(analysisResult));
  }

  private static String whatAnalyzed(int numFiles) {
    if (numFiles == 1) {
      return "1 file";
    } else {
      return numFiles + " files";
    }
  }
}