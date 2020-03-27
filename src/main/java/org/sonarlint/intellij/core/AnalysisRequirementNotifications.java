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

package org.sonarlint.intellij.core;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;

public class AnalysisRequirementNotifications {

  private AnalysisRequirementNotifications() {
    // NOP
  }

  public static final String GROUP_ANALYSIS_PROBLEM = "SonarLint: Analysis Requirement";

  public static void notifyNodeCommandException(Project project) {
    Notification notification = new Notification(GROUP_ANALYSIS_PROBLEM,
      "<b>SonarLint - Node.js Required</b>",
      "Node.js >= 8.x is required to perform JavaScript or TypeScript analysis. Check the <a href='#'>SonarLint Log</a> for details.",
      NotificationType.WARNING, new ShowSonarLintLogListener(project));
    notification.setImportant(true);
    notification.notify(project);
  }

  private static class ShowSonarLintLogListener implements NotificationListener {
    private final Project project;
    private ShowSonarLintLogListener(Project project) {
      this.project = project;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      notification.expire();
      ToolWindow toolWindow = getToolWindow();
      if (toolWindow != null) {
        toolWindow.show(() -> selectLogsTab(toolWindow));
      }
    }

    private ToolWindow getToolWindow() {
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
      return toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
    }

    private static void selectLogsTab(ToolWindow toolWindow) {
      ContentManager contentManager = toolWindow.getContentManager();
      Content content = contentManager.findContent(SonarLintToolWindowFactory.TAB_LOGS);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }

  }
}
