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
package org.sonarlint.intellij.util;

import com.intellij.openapi.project.ProjectManager;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;

public class GlobalLogOutputImpl implements GlobalLogOutput {

  @Override
  public void log(String msg, Level level) {
    switch (level) {
      case TRACE -> {
        // Do not log TRACE level messages to avoid flooding the console
      }
      case DEBUG -> getConsolesOfOpenedProjects().forEach(console -> console.debug(msg));
      case ERROR -> getConsolesOfOpenedProjects().forEach(console -> console.error(msg));
      default -> getConsolesOfOpenedProjects().forEach(console -> console.info(msg));
    }
  }

  @Override
  public void logError(String msg, @Nullable Throwable t) {
    getConsolesOfOpenedProjects()
      .forEach(sonarLintConsole -> sonarLintConsole.error(msg, t));
  }

  @NotNull
  private static Stream<SonarLintConsole> getConsolesOfOpenedProjects() {
    return Stream.of(ProjectManager.getInstance().getOpenProjects())
      .map(project -> SonarLintUtils.getService(project, SonarLintConsole.class));
  }

}
