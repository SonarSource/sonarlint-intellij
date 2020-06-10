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

import com.intellij.openapi.util.Disposer;
import java.util.LinkedList;
import java.util.List;
import org.sonarlint.intellij.ui.SonarLintConsole;

public class GlobalLogOutputImpl implements GlobalLogOutput {

  private final List<SonarLintConsole> consoles = new LinkedList<>();


  @Override
  public void log(String msg, Level level) {
    switch (level) {
      case TRACE:
      case DEBUG:
        for (SonarLintConsole c : consoles) {
          c.debug(msg);
        }
        break;
      case ERROR:
        for (SonarLintConsole c : consoles) {
          c.error(msg);
        }
        break;
      case INFO:
      case WARN:
      default:
        for (SonarLintConsole c : consoles) {
          c.info(msg);
        }
    }
  }

  @Override
  public void logError(String msg, Throwable t) {
    for (SonarLintConsole c : consoles) {
      c.error(msg, t);
    }
  }

  @Override
  public void addConsole(SonarLintConsole console) {
    this.consoles.add(console);
  }

  public void removeConsole(SonarLintConsole console) {
    this.consoles.remove(console);
  }

  @Override public void dispose() {
    Disposer.dispose(this);
  }

}
