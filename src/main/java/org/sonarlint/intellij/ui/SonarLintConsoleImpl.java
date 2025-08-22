/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.NonInjectable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class SonarLintConsoleImpl implements SonarLintConsole, Disposable {

  private ConsoleView consoleView;
  private final Project myProject;
  private final Queue<Log> previousLogs = new ConcurrentLinkedQueue<>();

  public SonarLintConsoleImpl(Project project) {
    this.myProject = project;
  }

  @NonInjectable
  SonarLintConsoleImpl(Project project, ConsoleView consoleView) {
    this.consoleView = consoleView;
    this.myProject = project;
  }

  @Override
  public void debug(String msg) {
    if (debugEnabled()) {
      print(msg, ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  @Override
  public boolean debugEnabled() {
    return getSettingsFor(myProject).isVerboseEnabled();
  }

  @Override
  public void info(String msg) {
    print(msg, ConsoleViewContentType.NORMAL_OUTPUT);
  }

  @Override
  public void error(String msg) {
    print(msg, ConsoleViewContentType.ERROR_OUTPUT);
  }

  private void print(String msg, ConsoleViewContentType outputType) {
    if (!myProject.isDisposed()) {
      if (consoleView == null) {
        previousLogs.offer(new Log(msg + "\n", outputType));
      } else {
        consoleView.print(msg + "\n", outputType);
      }
    }
  }

  @Override
  public void error(String msg, @Nullable Throwable t) {
    error(msg);
    if (t != null) {
      var errors = new StringWriter();
      t.printStackTrace(new PrintWriter(errors));
      error(errors.toString());
    }
  }

  @Override
  public void clear() {
    if (consoleView != null) {
      consoleView.clear();
    }
  }

  @Override
  public void setConsoleView(ConsoleView consoleView) {
    while (!previousLogs.isEmpty()) {
      var log = previousLogs.poll();
      consoleView.print(log.text, log.outputType);
    }
    this.consoleView = consoleView;
    Disposer.register(this, consoleView);
  }

  @Override
  public void dispose() {
    // nothing to do, the console view is already registered for dispose
  }

  private record Log(String text, ConsoleViewContentType outputType) {
  }

}
