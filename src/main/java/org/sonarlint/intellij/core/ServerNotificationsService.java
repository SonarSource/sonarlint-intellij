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
package org.sonarlint.intellij.core;

import com.intellij.openapi.Disposable;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotificationListener;
import org.sonarsource.sonarlint.core.notifications.ServerNotificationsRegistry;

public class ServerNotificationsService implements Disposable {

  private final ServerNotificationsRegistry serverNotificationsRegistry;

  public ServerNotificationsService() {
    serverNotificationsRegistry = new ServerNotificationsRegistry();
  }

  public static ServerNotificationsService get() {
    return SonarLintUtils.getService(ServerNotificationsService.class);
  }

  public void register(NotificationConfiguration configuration) {
    serverNotificationsRegistry.register(configuration);
  }

  public void unregister(ServerNotificationListener notificationListener) {
    serverNotificationsRegistry.remove(notificationListener);
  }

  public boolean isSupported(ServerConnection serverConnection) {
    return ServerNotificationsRegistry.isSupported(
      serverConnection.getEndpointParams(),
      serverConnection.getHttpClient()
    );
  }

  @Override
  public void dispose() {
    serverNotificationsRegistry.stop();
  }
}
