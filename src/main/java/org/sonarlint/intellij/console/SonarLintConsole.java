/*
 * SonarQube IntelliJ
 * Copyright (C) 2013-2014 SonarSource
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
package org.sonarlint.intellij.console;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.toolwindow.SonarLintToolWindowFactory;

public class SonarLintConsole extends AbstractProjectComponent {

  private ConsoleView consoleView;

  public SonarLintConsole(Project project) {
    super(project);
  }

  public static synchronized SonarLintConsole getSonarQubeConsole(@NotNull Project p) {
    return p.getComponent(SonarLintConsole.class);
  }

  private ConsoleView createConsoleView(Project project) {
    final ConsoleView newConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SonarLintToolWindowFactory.ID);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        toolWindow.show(new Runnable() {
          @Override
          public void run() {
            Content content = toolWindow.getContentManager().getFactory().createContent(newConsoleView.getComponent(), "Console", true);
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

  public void error(String msg, Throwable t) {
    error(msg);
    StringWriter errors = new StringWriter();
    t.printStackTrace(new PrintWriter(errors));
    error(errors.toString());
  }

  public void clear() {
    getConsoleView().clear();
  }

  private synchronized ConsoleView getConsoleView() {
    if (this.consoleView == null) {
      this.consoleView = createConsoleView(myProject);
    }
    return this.consoleView;
  }
}
