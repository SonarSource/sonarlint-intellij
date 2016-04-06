/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.core;

import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

public class ServerUpdateTask extends com.intellij.openapi.progress.Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ServerUpdateTask.class);
  private final ConnectedSonarLintEngine engine;
  private final SonarQubeServer server;
  private final String projectKey;
  private final boolean onlyModules;

  public ServerUpdateTask(ConnectedSonarLintEngine engine, SonarQubeServer server, @Nullable String projectKey, boolean onlyModules) {
    super(null, "Updating SonarQube server '" + server.getName() + "'", true);
    this.engine = engine;
    this.server = server;
    this.projectKey = projectKey;
    this.onlyModules = onlyModules;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Fetching data...");

    try {
      TaskProgressMonitor monitor = new TaskProgressMonitor(indicator);
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
      if (!onlyModules) {
        engine.update(serverConfiguration, monitor);
      }

      if (projectKey != null) {
        engine.updateModule(serverConfiguration, projectKey);
      }
      return;
    } catch (final Exception e) {
      LOGGER.info("Error updating server " + server.getName(), e);
      final String msg = (e.getMessage() != null) ? e.getMessage() :
        (e.getClass().getSimpleName() + " - please check the logs to see the stack trace");
      ApplicationManager.getApplication().invokeAndWait(new RunnableAdapter() {
        @Override public void doRun() throws Exception {
          Messages.showErrorDialog((Project) null, msg, "Update failed");
        }
      }, ModalityState.any());
    }
  }

}
