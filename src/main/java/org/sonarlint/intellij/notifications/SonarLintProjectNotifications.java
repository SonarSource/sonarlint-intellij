/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.notifications;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.common.util.SonarLintUtils;

public class SonarLintProjectNotifications {
  private static final NotificationGroup BINDING_PROBLEM_GROUP = NotificationGroupManager.getInstance()
    .getNotificationGroup("SonarLint: Server Binding Errors");
  public static final NotificationGroup SERVER_NOTIFICATIONS_GROUP = NotificationGroupManager.getInstance()
    .getNotificationGroup("SonarLint: Server Notifications");
  private static final String UPDATE_SERVER_MSG = "\n<br>Please update the binding in the SonarLint Settings";
  private static final String UPDATE_BINDING_MSG = "\n<br>Please check the SonarLint project configuration";
  private static final String TITLE_SONARLINT_INVALID_BINDING = "<b>SonarLint - Invalid binding</b>";
  private static final String NO_SUBTITLE = null;
  private volatile boolean shown = false;
  private final Project myProject;

  protected SonarLintProjectNotifications(Project project) {
    this.myProject = project;
  }

  public static SonarLintProjectNotifications get(Project project) {
    return SonarLintUtils.getService(project, SonarLintProjectNotifications.class);
  }

  public void reset() {
    shown = false;
  }

  public void notifyConnectionIdInvalid() {
    if (shown) {
      return;
    }
    var notification = BINDING_PROBLEM_GROUP.createNotification(
      TITLE_SONARLINT_INVALID_BINDING,
      NO_SUBTITLE,
      "Project bound to an invalid connection" + UPDATE_BINDING_MSG,
      NotificationType.WARNING);
    notification.addAction(new OpenProjectSettingsAction(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  public void notifyProjectStorageInvalid() {
    if (shown) {
      return;
    }
    var notification = BINDING_PROBLEM_GROUP.createNotification(
      TITLE_SONARLINT_INVALID_BINDING,
      NO_SUBTITLE,
      "Project bound to an invalid remote project" + UPDATE_BINDING_MSG,
      NotificationType.WARNING);
    notification.addAction(new OpenProjectSettingsAction(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  public void notifyProjectStorageStale() {
    if (shown) {
      return;
    }
    var notification = BINDING_PROBLEM_GROUP.createNotification(
      TITLE_SONARLINT_INVALID_BINDING,
      NO_SUBTITLE,
      "Local storage is outdated" + UPDATE_BINDING_MSG,
      NotificationType.WARNING);
    notification.addAction(new OpenProjectSettingsAction(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  public void notifyServerNeverUpdated(String serverId) {
    if (shown) {
      return;
    }
    var notification = BINDING_PROBLEM_GROUP.createNotification(
      TITLE_SONARLINT_INVALID_BINDING,
      NO_SUBTITLE,
      "Missing local storage for connection '" + serverId + "'" + UPDATE_SERVER_MSG,
      NotificationType.WARNING);
    notification.addAction(new OpenGlobalSettingsAction(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  public void notifyServerStorageNeedsUpdate(String serverId) {
    if (shown) {
      return;
    }
    var notification = BINDING_PROBLEM_GROUP.createNotification(
      TITLE_SONARLINT_INVALID_BINDING,
      NO_SUBTITLE,
      "Local storage for connection '" + serverId + "' must be updated" + UPDATE_SERVER_MSG,
      NotificationType.WARNING);
    notification.addAction(new OpenGlobalSettingsAction(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

}
