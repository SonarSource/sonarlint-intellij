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
package org.sonarlint.intellij.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.BackendService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ProgressUtils.waitForFuture;
import static org.sonarlint.intellij.util.ThreadUtilsKt.computeOnPooledThreadWithoutCatching;

public class ServerDownloadProjectTask extends Task.WithResult<Map<String, SonarProjectDto>, Exception> {
  private final ServerConnection server;
  private final Project project;

  public ServerDownloadProjectTask(Project project, ServerConnection server) {
    super(project, "Downloading Project List", true);
    this.project = project;
    this.server = server;
  }

  @Override
  protected Map<String, SonarProjectDto> compute(@NotNull ProgressIndicator indicator) {
    try {
      indicator.setIndeterminate(true);
      indicator.setText("Downloading projects");
      return computeOnPooledThreadWithoutCatching(project, "Download Projects Task",
        () ->
        waitForFuture(indicator, getService(BackendService.class)
          .getAllProjects(server))
          .getSonarProjects()
          .stream().collect(Collectors.toMap(SonarProjectDto::getKey, p -> p)));
    } catch (Exception e) {
      SonarLintConsole.get(project).error("Failed to download list of projects", e);
      throw e;
    }
  }

}
