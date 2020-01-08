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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.ui.content.Content;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.util.SonarLintUtils;

/**
 * Factory of SonarLint tool window.
 * Nothing can be injected as it runs in the root pico container.
 */
public class SonarLintToolWindowFactory implements ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = "SonarLint";
  public static final String TAB_LOGS = "Log";
  public static final String TAB_CURRENT_FILE = "Current file";
  public static final String TAB_ANALYSIS_RESULTS = "Report";

  @Override
  public void createToolWindowContent(Project project, final ToolWindow toolWindow) {
    addIssuesTab(project, toolWindow);
    addAnalysisResultsTab(project, toolWindow);
    addLogTab(project, toolWindow);
    toolWindow.setType(ToolWindowType.DOCKED, null);
  }

  private static void addIssuesTab(Project project, ToolWindow toolWindow) {
    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    IssueManager issueManager = SonarLintUtils.get(project, IssueManager.class);
    CurrentFileController scope = new CurrentFileController(project, issueManager);
    SonarLintIssuesPanel issuesPanel = new SonarLintIssuesPanel(project, projectBindingManager, scope);
    Content issuesContent = toolWindow.getContentManager().getFactory()
      .createContent(
        issuesPanel,
        TAB_CURRENT_FILE,
        false);
    toolWindow.getContentManager().addDataProvider(issuesPanel);
    toolWindow.getContentManager().addContent(issuesContent);
  }

  private static void addAnalysisResultsTab(Project project, ToolWindow toolWindow) {
    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    SonarLintAnalysisResultsPanel resultsPanel = new SonarLintAnalysisResultsPanel(project, projectBindingManager);
    Content analysisResultsContent = toolWindow.getContentManager().getFactory()
      .createContent(
        resultsPanel,
        TAB_ANALYSIS_RESULTS,
        false);
    toolWindow.getContentManager().addDataProvider(resultsPanel);
    toolWindow.getContentManager().addContent(analysisResultsContent);
  }

  private static void addLogTab(Project project, ToolWindow toolWindow) {
    Content logContent = toolWindow.getContentManager().getFactory()
      .createContent(
        new SonarLintLogPanel(toolWindow, project),
        TAB_LOGS,
        false);
    toolWindow.getContentManager().addContent(logContent);
  }
}
