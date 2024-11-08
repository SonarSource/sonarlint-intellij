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
package org.sonarlint.intellij.tasks;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.BackendService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.OrganizationDto;

import static org.sonarlint.intellij.util.ProgressUtils.waitForFuture;
import static org.sonarlint.intellij.util.ThreadUtilsKt.computeOnPooledThreadWithoutCatching;

/**
 * Only useful for SonarCloud
 */
public class GetOrganizationsTask extends Task.Modal {
  private final ServerConnection connection;
  private Exception exception;
  private List<OrganizationDto> organizations;

  public GetOrganizationsTask(ServerConnection connection) {
    super(null, "Fetch Organizations from SonarQube Cloud", true);
    this.connection = connection;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Searching organizations");
    indicator.setIndeterminate(true);
    try {
      organizations = computeOnPooledThreadWithoutCatching("Get User Organizations Task",
        () -> waitForFuture(indicator, SonarLintUtils.getService(BackendService.class).listUserOrganizations(connection)).getUserOrganizations());
    } catch (Exception e) {
      if (myProject != null) {
        SonarLintConsole.get(myProject).error("Failed to fetch organizations", e);
      }
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public List<OrganizationDto> organizations() {
    return organizations;
  }

}
