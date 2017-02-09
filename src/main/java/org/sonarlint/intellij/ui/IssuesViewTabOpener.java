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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

public class IssuesViewTabOpener {
  private final Project project;

  public IssuesViewTabOpener(Project project) {
    this.project = project;
  }

  /**
   * Must run in EDT
   */
  public void open(String tab) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
    if (toolWindow != null) {
      toolWindow.show(new ContentSelector(toolWindow, tab));
    }
  }

  private static class ContentSelector implements Runnable {
    private final ToolWindow toolWindow;
    private final String tab;

    private ContentSelector(ToolWindow toolWindow, String tab) {
      this.toolWindow = toolWindow;
      this.tab = tab;
    }

    @Override public void run() {
      ContentManager contentManager = toolWindow.getContentManager();
      Content content = contentManager.findContent(tab);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }
  }
}
