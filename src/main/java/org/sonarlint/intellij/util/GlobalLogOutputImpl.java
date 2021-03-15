/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.project.ProjectManager;
import java.util.Arrays;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

public class GlobalLogOutputImpl implements GlobalLogOutput {

  @Override
  public void log(String msg, Level level) {
    switch (level) {
      case TRACE:
      case DEBUG:
          getConsolesOfOpenedProjects().forEach(console -> console.debug(msg));
        break;
      case ERROR:
        getConsolesOfOpenedProjects().forEach(console -> console.error(msg));
        break;
      case INFO:
      case WARN:
      default:
        getConsolesOfOpenedProjects().forEach(console -> console.info(msg));
    }
  }

  @Override
  public void logError(String msg, Throwable t) {
    getConsolesOfOpenedProjects()
      .forEach(sonarLintConsole -> sonarLintConsole.error(msg, t));
  }

  @NotNull
  private Stream<SonarLintConsole> getConsolesOfOpenedProjects() {
    return Arrays.stream(ProjectManager.getInstance().getOpenProjects())
      .map(project -> SonarLintUtils.getService(project, SonarLintConsole.class));
  }

}
