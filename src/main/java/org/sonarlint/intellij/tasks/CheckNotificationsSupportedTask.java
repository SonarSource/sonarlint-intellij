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
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.notifications.ServerNotifications;

/**
 * Only useful for SonarQube, since we know notifications are available in SonarCloud
 */
public class CheckNotificationsSupportedTask extends Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ConnectionTestTask.class);
  private final ServerConnection connection;
  private Exception exception;
  private boolean notificationsSupported = false;

  public CheckNotificationsSupportedTask(ServerConnection connection) {
    super(null, "Check if smart notifications is available in the SonarQube edition", true);
    this.connection = connection;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Connecting to " + connection.getHostUrl() + "...");
    indicator.setIndeterminate(false);

    try {
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(connection);
      if (connection.isSonarCloud()) {
        notificationsSupported = true;
      } else {
        indicator.setText("Checking support of notifications");
        notificationsSupported = ServerNotifications.get().isSupported(serverConfiguration);
      }
    } catch (Exception e) {
      LOGGER.info("Failed to check notifications", e);
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public boolean notificationsSupported() {
    return notificationsSupported;
  }

}
