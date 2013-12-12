/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.console;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.toolwindow.SonarQubeToolWindowFactory;

import javax.swing.*;

public class SonarQubeConsole implements ProjectComponent {

  private final Project project;
  private ConsoleView consoleView;

  public SonarQubeConsole(Project project) {
    this.project = project;
  }

  public static synchronized SonarQubeConsole getSonarQubeConsole(@NotNull Project p) {
    return p.getComponent(SonarQubeConsole.class);
  }

  private ConsoleView createConsoleView(Project project) {
    final ConsoleView newConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SonarQubeToolWindowFactory.ID);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        toolWindow.show(new Runnable() {
          @Override
          public void run() {
            Content content = toolWindow.getContentManager().getFactory().createContent(newConsoleView.getComponent(), "SonarQube Console", true);
            toolWindow.getContentManager().addContent(content);
          }
        });
      }
    });
    return newConsoleView;
  }

  public void info(String msg) {
    getConsoleView().print(msg + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  public void error(String msg) {
    getConsoleView().print(msg + "\n", ConsoleViewContentType.ERROR_OUTPUT);
  }

  public void clear() {
    getConsoleView().clear();
  }

  private synchronized ConsoleView getConsoleView() {
    if (this.consoleView == null) {
      this.consoleView = createConsoleView(project);
    }
    return this.consoleView;
  }

  @Override
  public void projectOpened() {
    //Nothing to do
  }

  @Override
  public void projectClosed() {
    //Nothing to do
  }

  @Override
  public void initComponent() {
    //Nothing to do
  }

  @Override
  public void disposeComponent() {
    //Nothing to do
  }

  @NotNull
  @Override
  public String getComponentName() {
    return this.getClass().getSimpleName();
  }
}
