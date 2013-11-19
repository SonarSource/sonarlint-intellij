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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.sonar.ide.intellij.toolwindow.SonarQubeToolWindowFactory;

import javax.swing.*;

public class SonarQubeConsole {

  private static SonarQubeConsole INSTANCE;

  private final ConsoleView consoleView;

  private SonarQubeConsole(Project project) {
     this.consoleView = getConsoleView(project);
  }

  public static synchronized SonarQubeConsole getSonarQubeConsole(Project p) {
     if (INSTANCE == null) {
       INSTANCE = new SonarQubeConsole(p);
     }
    return INSTANCE;
  }

  private ConsoleView getConsoleView(Project project) {
    final ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SonarQubeToolWindowFactory.ID);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        toolWindow.show(new Runnable() {
          @Override
          public void run() {
            Content content = toolWindow.getContentManager().getFactory().createContent(consoleView.getComponent(), "SonarQube Console", true);
            toolWindow.getContentManager().addContent(content);
          }
        });
      }
    });
    return consoleView;
  }

  public void info(String msg) {
    consoleView.print(msg + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  public void error(String msg) {
    consoleView.print(msg + "\n", ConsoleViewContentType.ERROR_OUTPUT);
  }
}
