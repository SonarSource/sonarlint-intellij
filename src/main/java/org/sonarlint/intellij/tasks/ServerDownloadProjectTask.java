/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

public class ServerDownloadProjectTask extends Task.WithResult<Map<String, ServerProject>, Exception> {
  private final ConnectedSonarLintEngine engine;
  private final ServerConnection server;

  public ServerDownloadProjectTask(Project project, ConnectedSonarLintEngine engine, ServerConnection server) {
    super(project, "Downloading Project List", true);
    this.engine = engine;
    this.server = server;
  }

  @Override
  protected Map<String, ServerProject> compute(@NotNull ProgressIndicator indicator) throws Exception {
    try {
      var monitor = new TaskProgressMonitor(indicator, myProject);
      return engine.downloadAllProjects(server.getEndpointParams(), server.getHttpClient(), monitor);
    } catch (Exception e) {
      SonarLintConsole.get(myProject).error("Failed to download list of projects", e);
      throw e;
    }
  }

}
