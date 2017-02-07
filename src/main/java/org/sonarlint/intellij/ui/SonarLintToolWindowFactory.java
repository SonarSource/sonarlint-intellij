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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.ui.content.Content;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.AllFilesIssues;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
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
  public static final String TAB_CHANGED_FILES = "Changed files";
  public static final String TAB_ALL_FILES = "All files";

  private Content changedFilesTab;

  @Override
  public void createToolWindowContent(Project project, final ToolWindow toolWindow) {
    addIssuesTab(project, toolWindow);
    addChangedFilesTab(project, toolWindow);
    addAllFilesTab(project, toolWindow);
    addLogTab(project, toolWindow);
    toolWindow.setType(ToolWindowType.DOCKED, null);

    project.getMessageBus().connect(project)
      .subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
        () -> ApplicationManager.getApplication().invokeLater(() -> vcsChange(project)));
  }

  private static void addIssuesTab(Project project, ToolWindow toolWindow) {
    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    IssueManager issueManager = SonarLintUtils.get(project, IssueManager.class);
    SonarLintIssuesPanel issuesPanel = new SonarLintIssuesPanel(project, issueManager, projectBindingManager);
    Content issuesContent = toolWindow.getContentManager().getFactory()
      .createContent(
        issuesPanel,
        TAB_CURRENT_FILE,
        false);
    toolWindow.getContentManager().addDataProvider(issuesPanel::getData);
    toolWindow.getContentManager().addContent(issuesContent);
  }

  private void addChangedFilesTab(Project project, ToolWindow toolWindow) {
    ChangedFilesIssues changedFileIssues = SonarLintUtils.get(project, ChangedFilesIssues.class);
    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    SonarLintChangedPanel changedPanel = new SonarLintChangedPanel(project, changedFileIssues, projectBindingManager);
    Content changedContent = toolWindow.getContentManager().getFactory()
      .createContent(
        changedPanel,
        TAB_CHANGED_FILES,
        false);
    changedFilesTab = changedContent;
    if (hasVcs(project)) {
      toolWindow.getContentManager().addContent(changedContent);
    }
  }

  private void addAllFilesTab(Project project, ToolWindow toolWindow) {
    AllFilesIssues allFileIssues = SonarLintUtils.get(project, AllFilesIssues.class);
    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    SonarLintAllFilesPanel resultsPanel = new SonarLintAllFilesPanel(project, allFileIssues, projectBindingManager);
    Content allFilesContent = toolWindow.getContentManager().getFactory()
      .createContent(
        resultsPanel,
        TAB_ALL_FILES,
        false);
    toolWindow.getContentManager().addContent(allFilesContent);
  }

  private static void addLogTab(Project project, ToolWindow toolWindow) {
    Content logContent = toolWindow.getContentManager().getFactory()
      .createContent(
        new SonarLintLogPanel(toolWindow, project),
        TAB_LOGS,
        false);
    toolWindow.getContentManager().addContent(logContent);
  }

  private static boolean hasVcs(Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return vcsManager.hasActiveVcss();
  }

  private void vcsChange(Project project) {
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = manager.getToolWindow(TOOL_WINDOW_ID);
    boolean hasVcs = hasVcs(project);
    if (toolWindow == null) {
      return;
    }
    Content content = toolWindow.getContentManager().findContent(TAB_CHANGED_FILES);

    if (content != null && !hasVcs) {
      toolWindow.getContentManager().removeContent(content, false);
    } else if (content == null && hasVcs) {
      toolWindow.getContentManager().addContent(changedFilesTab, 1);
    }
  }
}
