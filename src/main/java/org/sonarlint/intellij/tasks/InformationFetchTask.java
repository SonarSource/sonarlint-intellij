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
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.notifications.SonarQubeNotifications;

public class InformationFetchTask extends Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ConnectionTestTask.class);
  private final SonarQubeServer server;
  private Exception exception;
  private List<RemoteOrganization> organizations;
  private boolean notificationsSupported = false;

  public InformationFetchTask(SonarQubeServer server) {
    super(null, "Fetch Information From SonarQube Server", true);
    this.server = server;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Connecting to " + server.getHostUrl() + "...");
    indicator.setIndeterminate(false);

    try {
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
      indicator.setText("Checking support of notifications");
      notificationsSupported = SonarQubeNotifications.get().isSupported(serverConfiguration);
      WsHelper wsHelper = new WsHelperImpl();
      organizations = wsHelper.listUserOrganizations(serverConfiguration, new TaskProgressMonitor(indicator));
    } catch (UnsupportedServerException e) {
      organizations = Collections.emptyList();
    } catch (Exception e) {
      LOGGER.info("Failed to fetch information", e);
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public List<RemoteOrganization> organizations() {
    return organizations;
  }

  public boolean notificationsSupported() {
    return notificationsSupported;
  }

}
