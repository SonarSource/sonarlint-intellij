/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import java.io.PrintStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

public class SonarLintConsoleTestImpl implements SonarLintConsole {

  private final Queue<String> previousMessages = new ConcurrentLinkedQueue<>();

  public String getLastMessage() {
    return previousMessages.peek();
  }

  @Override
  public void debug(String msg) {
    print(msg);
  }

  @Override
  public boolean debugEnabled() {
    return true;
  }

  @Override
  public void info(String msg) {
    print(msg);
  }

  @Override
  public void error(String msg) {
    print(msg);
  }

  @Override
  public void error(String msg, Throwable t) {
    print(msg);
    for (StackTraceElement traceElement : t.getStackTrace()) {
      print(traceElement.toString());
    }
  }

  private void print(String msg) {
    previousMessages.offer(msg);
  }

  @Override
  public void clear() {
    previousMessages.clear();
  }

  public void flushTo(PrintStream stream) {
    while (!previousMessages.isEmpty()) {
      stream.println(previousMessages.poll());
    }
  }

  @Override
  public void setConsoleView(ConsoleView consoleView) { }

}
