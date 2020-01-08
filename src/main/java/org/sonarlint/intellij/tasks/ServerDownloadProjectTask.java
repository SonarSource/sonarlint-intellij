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
package org.sonarlint.intellij.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

// we can't use Task.WithResult because it was only introduced recently
public class ServerDownloadProjectTask extends Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ServerDownloadProjectTask.class);
  private final ConnectedSonarLintEngine engine;
  private final SonarQubeServer server;

  private Exception exception;
  private Map<String, RemoteProject> result;

  public ServerDownloadProjectTask(Project project, ConnectedSonarLintEngine engine, SonarQubeServer server) {
    super(project, "Downloading Project List", true);
    this.engine = engine;
    this.server = server;
  }

  @Override public void run(@NotNull ProgressIndicator indicator) {
    try {
      TaskProgressMonitor monitor = new TaskProgressMonitor(indicator);
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
      this.result = engine.downloadAllProjects(serverConfiguration, monitor);
    } catch (Exception e) {
      LOGGER.info("Failed to download list of projects", e);
      this.exception = e;
    }
  }

  public Map<String, RemoteProject> getResult() throws Exception {
    if (exception != null) {
      throw exception;
    }
    return result;
  }
}
