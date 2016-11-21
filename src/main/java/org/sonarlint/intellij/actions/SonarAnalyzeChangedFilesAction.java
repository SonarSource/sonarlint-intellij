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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.concurrent.Future;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarAnalyzeChangedFilesAction extends AbstractSonarAction {
  private static final Logger LOGGER = Logger.getInstance(SonarAnalyzeChangedFilesAction.class);

  @Override protected boolean isEnabled(Project project, SonarLintStatus status) {
    return !status.isRunning();
  }

  @Override public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();

    if (project == null) {
      return;
    }

    SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
    ChangedFilesIssues changedFilesIssues = SonarLintUtils.get(project, ChangedFilesIssues.class);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    List<VirtualFile> affectedFiles = changeListManager.getAffectedFiles();
    Future<AnalysisResult> future = submitter.submitFiles(affectedFiles.toArray(new VirtualFile[affectedFiles.size()]), TriggerType.ACTION, false, false);
    try {
      AnalysisResult result = future.get();
      changedFilesIssues.set(result.issues());
    } catch (Exception ex) {
      LOGGER.error("Failed to analyse changed files", ex);
    }
  }
}
