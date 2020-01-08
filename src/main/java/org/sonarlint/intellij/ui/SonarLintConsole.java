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

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

public class SonarLintConsole extends AbstractProjectComponent {

  private final ConsoleView consoleView;
  private final SonarLintProjectSettings settings;

  public SonarLintConsole(Project project) {
    super(project);
    consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    settings = myProject.getComponent(SonarLintProjectSettings.class);
  }

  /**
   * TODO Replace @Deprecated with @NonInjectable when switching to 2019.3 API level
   * @deprecated in 4.2 to silence a check in 2019.3
   */
  @Deprecated
  SonarLintConsole(Project project, ConsoleView consoleView, SonarLintProjectSettings settings) {
    super(project);
    this.consoleView = consoleView;
    this.settings = settings;
  }

  @Override
  public void projectClosed() {
    // if we do it when Project is disposed, it's too late
    Disposer.dispose(consoleView);
  }

  public static synchronized SonarLintConsole get(@NotNull Project p) {
    return p.getComponent(SonarLintConsole.class);
  }

  public void debug(String msg) {
    if (settings.isVerboseEnabled()) {
      getConsoleView().print(msg + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  public boolean debugEnabled() {
    return settings.isVerboseEnabled();
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

  public ConsoleView getConsoleView() {
    return this.consoleView;
  }
}
