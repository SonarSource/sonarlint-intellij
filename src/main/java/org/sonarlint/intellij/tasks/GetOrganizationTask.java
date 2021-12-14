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
package org.sonarlint.intellij.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.organization.ServerOrganization;

public class GetOrganizationTask extends Task.Modal {
  private final ServerConnection server;
  private final String organizationKey;

  private Exception exception;
  private Optional<ServerOrganization> organization = Optional.empty();

  public GetOrganizationTask(ServerConnection server, String organizationKey) {
    super(null, "Fetch Organization From SonarCloud", true);
    this.server = server;
    this.organizationKey = organizationKey;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Connecting to SonarCloud...");
    indicator.setIndeterminate(false);

    try {
      indicator.setText("Searching organization");
      organization = server.api().organization().getOrganization(organizationKey, new ProgressMonitor(new TaskProgressMonitor(indicator, myProject)));
    } catch (Exception e) {
      SonarLintConsole.get(myProject).error("Failed to fetch organizations", e);
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public Optional<ServerOrganization> organization() {
    return organization;
  }
}
