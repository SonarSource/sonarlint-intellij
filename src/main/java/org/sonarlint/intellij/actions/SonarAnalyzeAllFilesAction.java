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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.issue.AllFilesIssues;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarAnalyzeAllFilesAction extends AbstractSonarAction {
  public SonarAnalyzeAllFilesAction() {
    super();
  }

  public SonarAnalyzeAllFilesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override protected boolean isEnabled(Project project, SonarLintStatus status) {
    return !status.isRunning();
  }

  @Override public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();

    if (project == null) {
      return;
    }

    SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
    Collection<VirtualFile> allFiles = getAllFiles(project);
    AnalysisCallback callback = new ShowIssuesCallable(project, allFiles);
    submitter.submitFiles(allFiles, TriggerType.ACTION, callback, false);
  }

  private static Collection<VirtualFile> getAllFiles(Project project) {
    List<VirtualFile> fileList = new ArrayList<>();
    ProjectFileIndex fileIndex = SonarLintUtils.get(project, ProjectRootManager.class).getFileIndex();
    fileIndex.iterateContent(vFile -> {
      if (!vFile.isDirectory() && !ProjectCoreUtil.isProjectOrWorkspaceFile(vFile, vFile.getFileType())) {
        fileList.add(vFile);
      }
      return true;
    });
    return fileList;
  }

  private static class ShowIssuesCallable implements AnalysisCallback {
    private final Project project;
    private final AllFilesIssues allFilesIssues;
    private final Collection<VirtualFile> affectedFiles;
    private final IssueManager issueManager;

    private ShowIssuesCallable(Project project, Collection<VirtualFile> affectedFiles) {
      this.project = project;
      this.allFilesIssues = SonarLintUtils.get(project, AllFilesIssues.class);
      this.issueManager = SonarLintUtils.get(project, IssueManager.class);
      this.affectedFiles = affectedFiles;
    }

    @Override public void onError(Throwable e) {
      // do nothing
    }

    @Override
    public void onSuccess() {
      Map<VirtualFile, Collection<LiveIssue>> map = affectedFiles.stream()
        .collect(Collectors.toMap(Function.identity(), issueManager::getForFile));
      allFilesIssues.set(map);
      showAllFilesTab();
    }

    private void showAllFilesTab() {
      UIUtil.invokeLaterIfNeeded(() -> ServiceManager.getService(project, IssuesViewTabOpener.class).open(SonarLintToolWindowFactory.TAB_ANALYSIS_RESULTS, false));
    }
  }
}
