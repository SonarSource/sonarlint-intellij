/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class AnalysisLogOutput implements ClientLogOutput, AutoCloseable {
  private Project project;

  public AnalysisLogOutput(Project project) {
    this.project = project;
  }

  @Override
  public void log(String msg, Level level) {
    var currentProject = project;
    if (currentProject == null || currentProject.isDisposed()) {
      return;
    }
    if (!getSettingsFor(currentProject).isAnalysisLogsEnabled()) {
      return;
    }
    var console = SonarLintUtils.getService(currentProject, SonarLintConsole.class);
    switch (level) {
      case TRACE, DEBUG -> console.debug(msg);
      case ERROR -> console.error(msg);
      default -> console.info(msg);
    }
  }

  @Override
  public void close() {
    // dereference the project after the analysis as the engine's lifecycle can be longer than the project's one
    this.project = null;
  }
}
