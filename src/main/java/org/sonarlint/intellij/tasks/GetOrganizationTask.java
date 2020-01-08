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
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

public class GetOrganizationTask extends Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(GetOrganizationTask.class);
  private final SonarQubeServer server;
  private final String organizationKey;

  private Exception exception;
  private Optional<RemoteOrganization> organization;

  public GetOrganizationTask(SonarQubeServer server, String organizationKey) {
    super(null, "Fetch Organization From SonarQube Server", true);
    this.server = server;
    this.organizationKey = organizationKey;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Connecting to " + server.getHostUrl() + "...");
    indicator.setIndeterminate(false);

    try {
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
      indicator.setText("Searching organization");
      WsHelper wsHelper = new WsHelperImpl();
      organization = wsHelper.getOrganization(serverConfiguration, organizationKey, new TaskProgressMonitor(indicator));
    } catch (Exception e) {
      LOGGER.info("Failed to fetch information", e);
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public Optional<RemoteOrganization> organization() {
    return organization;
  }
}
