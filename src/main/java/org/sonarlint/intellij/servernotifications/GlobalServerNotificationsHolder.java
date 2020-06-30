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
package org.sonarlint.intellij.servernotifications;

import com.intellij.openapi.Disposable;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotificationListener;
import org.sonarsource.sonarlint.core.notifications.SonarQubeNotifications;

/**
 * Keep track of the {@link SonarQubeNotifications} singleton. Lazy init at first use, and release it when application is disposed.
 */
public class GlobalServerNotificationsHolder implements Disposable {

  private SonarQubeNotifications coreServerNotifications;

  void register(NotificationConfiguration configuration) {
    getServerNotifications().register(configuration);
  }

  void unregister(SonarQubeNotificationListener notificationListener) {
    if (coreServerNotifications != null) {
      getServerNotifications().remove(notificationListener);
    }
  }

  private SonarQubeNotifications getServerNotifications() {
    if (coreServerNotifications == null) {
      coreServerNotifications = SonarQubeNotifications.get();
    }
    return coreServerNotifications;
  }

  @Override
  public void dispose() {
    if (coreServerNotifications != null) {
      coreServerNotifications.stop();
    }
  }

}
