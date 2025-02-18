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
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.BackendService;
import org.sonarsource.sonarlint.core.SonarCloudRegion;

import static org.sonarlint.intellij.util.ProgressUtils.waitForFuture;
import static org.sonarlint.intellij.util.ThreadUtilsKt.computeOnPooledThreadWithoutCatching;

/**
 * Only useful for SonarQube, since we know notifications are available in SonarCloud
 */
public class CheckNotificationsSupportedTask extends Task.Modal {
  private final ServerConnection connection;
  private final SonarCloudRegion region;
  private Exception exception;
  private boolean notificationsSupported = false;

  public CheckNotificationsSupportedTask(ServerConnection connection, SonarCloudRegion region) {
    super(null, "Check If Smart Notifications Are Supported", true);
    this.connection = connection;
    this.region = region;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    try {
      indicator.setText("Checking support of notifications");
      notificationsSupported = Boolean.TRUE.equals(computeOnPooledThreadWithoutCatching("Check Smart Notifications Supported Task",
        () -> waitForFuture(indicator, SonarLintUtils.getService(BackendService.class).checkSmartNotificationsSupported(connection, region)).isSuccess()));
    } catch (Exception e) {
      if (myProject != null) {
        SonarLintConsole.get(myProject).error("Failed to check notifications", e);
      }
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
