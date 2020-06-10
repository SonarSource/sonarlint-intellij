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
package org.sonarlint.intellij.util;

import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

public class GlobalLogOutputTestImpl implements GlobalLogOutput {

  private String lastMsg = "";

  @Override
  public void removeConsole(SonarLintConsole console) {

  }

  @Override
  public void log(String msg, LogOutput.Level level) {

  }

  @Override
  public void logError(String msg, Throwable t) {

  }

  @Override
  public void addConsole(SonarLintConsole console) {

  }

  @Override
  public void dispose() {

  }

  public String getLastMsg() {
    return lastMsg;
  }
}
