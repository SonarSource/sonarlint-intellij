/**
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.ui.content.Content;

public class SonarLintToolWindowFactory implements ToolWindowFactory {
  public static final String ID = "Analysis";

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    addAnalyzeTab(project, toolWindow);
    toolWindow.setTitle(ID);
    toolWindow.setType(ToolWindowType.DOCKED, null);
  }

  private static void addAnalyzeTab(Project project, ToolWindow toolWindow) {
    Content toolContent = toolWindow.getContentManager().getFactory().createContent(
      new SonarLintToolPanel(toolWindow, project),
      "Analyze",
      true);
    toolWindow.getContentManager().addContent(toolContent);
  }
}
