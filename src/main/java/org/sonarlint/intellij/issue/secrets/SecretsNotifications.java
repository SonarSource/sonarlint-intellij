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
package org.sonarlint.intellij.issue.secrets;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

public class SecretsNotifications {

  public static final NotificationGroup GROUP = NotificationGroup.balloonGroup("SonarLint: Secrets detection");

  private SecretsNotifications() {
    // utility class
  }

  public static void sendNotification(Project project) {
    Notification notification = GROUP.createNotification(
      "SonarLint detected leak of the secret",
      "You got this notification because SonarLint detected leak of the secret for the first time on this machine. " +
        "Remove it before commit or your data will be compromised. Next findings will be displayed among other issues on SonarLint panel.",
      NotificationType.WARNING, null);
    notification.setImportant(true);
    notification.notify(project);
  }
}
