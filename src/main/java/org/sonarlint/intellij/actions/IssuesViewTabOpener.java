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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import javax.annotation.CheckForNull;
import javax.swing.JComponent;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;

public class IssuesViewTabOpener {
  private final Project project;

  public IssuesViewTabOpener(Project project) {
    this.project = project;
  }

  /**
   * Must run in EDT
   */
  public void openAnalysisResults() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ToolWindow toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, SonarLintToolWindowFactory.TAB_ANALYSIS_RESULTS));
    }
  }

  /**
   * Must run in EDT
   */
  public void openCurrentFile() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ToolWindow toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, SonarLintToolWindowFactory.TAB_CURRENT_FILE));
    }
  }

  private ToolWindow getToolWindow() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    return toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
  }

  @CheckForNull
  private static JComponent selectTab(ToolWindow toolWindow, String tabId) {
    ContentManager contentManager = toolWindow.getContentManager();
    Content content = contentManager.findContent(tabId);
    if (content != null) {
      contentManager.setSelectedContent(content);
      return content.getComponent();
    }
    return null;
  }
}
