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

import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.notifications.AnalysisRequirementNotifications;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ProjectLogOutput implements ClientLogOutput {
  private final Project project;

  public ProjectLogOutput(Project project) {
    this.project = project;
  }

  @Override
  public void log(String msg, Level level) {
    if (project.isDisposed()) {
      return;
    }
    var console = SonarLintUtils.getService(project, SonarLintConsole.class);
    if (isNodeCommandException(msg)) {
      console.info(msg);
      AnalysisRequirementNotifications.notifyNodeCommandException(project);
      // Avoid duplicate log (info + debug)
      return;
    }
    if (!getSettingsFor(project).isAnalysisLogsEnabled()) {
      return;
    }
    switch (level) {
      case TRACE:
      case DEBUG:
        console.debug(msg);
        break;
      case ERROR:
        console.error(msg);
        break;
      case INFO:
      case WARN:
      default:
        console.info(msg);
    }
  }

  private static boolean isNodeCommandException(String msg) {
    return msg.contains("NodeCommandException");
  }
}
