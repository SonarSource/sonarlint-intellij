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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.ChangedFilesTabOpener;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarAnalyzeChangedFilesAction extends AbstractSonarAction {
  @Override protected boolean isEnabled(Project project, SonarLintStatus status) {
    if (status.isRunning()) {
      return false;
    }
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    return !changeListManager.getAffectedFiles().isEmpty();
  }

  @Override public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();

    if (project == null) {
      return;
    }

    SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    AnalysisCallback callback = new ShowIssuesCallable(project);
    List<VirtualFile> affectedFiles = changeListManager.getAffectedFiles();
    submitter.submitFiles(affectedFiles, TriggerType.ACTION, callback, false);
  }

  private class ShowIssuesCallable implements AnalysisCallback {
    private final Project project;

    private ShowIssuesCallable(Project project) {
      this.project = project;
    }

    @Override public void onSuccess(Map<VirtualFile, Collection<LiveIssue>> issues) {
      ChangedFilesIssues changedFilesIssues = SonarLintUtils.get(project, ChangedFilesIssues.class);
      changedFilesIssues.set(issues);
      showChangedFilesTab(project);
    }

    @Override public void onError(Exception e) {
      // do nothing
    }

    private void showChangedFilesTab(Project project) {
      UIUtil.invokeLaterIfNeeded(() -> {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
        if (toolWindow != null) {
          toolWindow.show(new ChangedFilesTabOpener(toolWindow));
        }
      });
    }
  }
}
