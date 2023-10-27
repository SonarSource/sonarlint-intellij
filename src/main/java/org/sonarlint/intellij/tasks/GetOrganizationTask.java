/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.wizard.PartialConnection;
import org.sonarlint.intellij.core.BackendService;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.OrganizationDto;

import static org.sonarlint.intellij.util.ProgressUtils.waitForFuture;

public class GetOrganizationTask extends Task.Modal {
  private final PartialConnection connection;
  private final String organizationKey;

  private Exception exception;
  private OrganizationDto organization;

  public GetOrganizationTask(PartialConnection connection, String organizationKey) {
    super(null, "Fetch Organization From SonarCloud", true);
    this.connection = connection;
    this.organizationKey = organizationKey;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Connecting to SonarCloud...");
    indicator.setIndeterminate(false);

    try {
      indicator.setText("Searching organization");
      organization = waitForFuture(indicator, SonarLintUtils.getService(BackendService.class).getOrganization(connection, organizationKey)).getOrganization();
    } catch (Exception e) {
      SonarLintConsole.get(myProject).error("Failed to fetch organizations", e);
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public OrganizationDto organization() {
    return organization;
  }
}
