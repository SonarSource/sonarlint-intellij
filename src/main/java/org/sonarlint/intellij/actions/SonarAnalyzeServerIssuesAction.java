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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.issue.ServerIssues;
import org.sonarlint.intellij.tasks.ServerIssuesTask;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

public class SonarAnalyzeServerIssuesAction extends AbstractSonarAction {
  @Override protected boolean isEnabled(Project project, SonarLintStatus status) {
    return !status.isRunning();
  }

  @Override public void actionPerformed(AnActionEvent e) {
    analyzeServerIssues(e.getProject());
  }

  public static void analyzeServerIssues(Project project) {
    if (project == null) {
      return;
    }

    SonarLintProjectSettings projectSettings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    if (!projectSettings.isBindingEnabled() || projectSettings.getProjectKey() == null || projectSettings.getServerId() == null) {
      return;
    }

    IssueProcessor issueProcessor = SonarLintUtils.get(project, IssueProcessor.class);
    ServerIssues serverIssues = SonarLintUtils.get(project, ServerIssues.class);
    ProjectBindingManager bindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    SonarQubeServer server = bindingManager.getSonarQubeServer();
    ConnectedSonarLintEngine engine = bindingManager.getConnectedEngineSkipChecks();

    ServerIssuesTask task = new ServerIssuesTask(project, projectSettings, issueProcessor, engine, server, serverIssues);
    ProgressManager.getInstance().run(task.asBackground());
  }

}
