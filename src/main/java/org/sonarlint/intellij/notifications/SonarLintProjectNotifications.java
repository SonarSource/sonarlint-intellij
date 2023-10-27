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
package org.sonarlint.intellij.notifications;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.ServerConnectionService;
import org.sonarlint.intellij.notifications.binding.BindProjectAction;
import org.sonarlint.intellij.notifications.binding.BindingSuggestion;
import org.sonarlint.intellij.notifications.binding.ChooseBindingSuggestionAction;
import org.sonarlint.intellij.notifications.binding.DisableBindingSuggestionsAction;
import org.sonarlint.intellij.notifications.binding.LearnMoreAboutConnectedModeAction;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

@Service(Service.Level.PROJECT)
public final class SonarLintProjectNotifications {
  private static final NotificationGroup BINDING_PROBLEM_GROUP = NotificationGroupManager.getInstance()
    .getNotificationGroup("SonarLint: Server Binding Errors");
  public static final NotificationGroup SERVER_NOTIFICATIONS_GROUP = NotificationGroupManager.getInstance()
    .getNotificationGroup("SonarLint: Server Notifications");
  private static final NotificationGroup BINDING_SUGGESTION_GROUP = NotificationGroupManager.getInstance()
    .getNotificationGroup("SonarLint: Binding Suggestions");
  private static final NotificationGroup OPEN_IN_IDE_GROUP = NotificationGroupManager.getInstance()
    .getNotificationGroup("SonarLint: Open in IDE");
  private static final String UPDATE_BINDING_MSG = "\n<br>Please check the SonarLint project configuration";
  private static final String TITLE_SONARLINT_INVALID_BINDING = "<b>SonarLint - Invalid binding</b>";
  private volatile boolean shown = false;
  private final Project myProject;

  private Notification currentOpenFindingNotification;

  public SonarLintProjectNotifications(Project project) {
    this.myProject = project;
  }

  public static SonarLintProjectNotifications get(Project project) {
    return getService(project, SonarLintProjectNotifications.class);
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
      "Project bound to an invalid remote project" + UPDATE_BINDING_MSG,
      NotificationType.WARNING);
    notification.addAction(new OpenProjectSettingsAction(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  public void suggestBindingOptions(List<BindingSuggestion> suggestedBindings) {
    if (suggestedBindings.size() == 1) {
      var suggestedBinding = suggestedBindings.get(0);
      notifyBindingSuggestions("Bind this project to '" + suggestedBinding.getProjectName() + "' on '" + suggestedBinding.getConnectionId() + "'?",
        new BindProjectAction(suggestedBinding), new OpenProjectSettingsAction(myProject, "Select another one"));
    } else {
      notifyBindingSuggestions("Bind this project to SonarQube or SonarCloud?",
        suggestedBindings.isEmpty() ? new OpenProjectSettingsAction(myProject, "Configure binding") : new ChooseBindingSuggestionAction(suggestedBindings));
    }
  }

  private void notifyBindingSuggestions(String message, AnAction... mainActions) {
    var notification = BINDING_SUGGESTION_GROUP.createNotification(
      "<b>SonarLint suggestions</b>",
      message,
      NotificationType.INFORMATION);
    Arrays.stream(mainActions).forEach(notification::addAction);
    notification.addAction(new LearnMoreAboutConnectedModeAction());
    notification.addAction(new DisableBindingSuggestionsAction());
    notification.setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST);
    notification.setImportant(true);
    notification.setIcon(SonarLintIcons.SONARLINT);
    notification.notify(myProject);
  }

  public void notifyUnableToOpenFinding(String type, String message, AnAction... mainActions) {
    expireCurrentFindingNotificationIfNeeded();
    currentOpenFindingNotification = OPEN_IN_IDE_GROUP.createNotification(
      "<b>SonarLint - Unable to open " + type + "</b>",
      message,
      NotificationType.INFORMATION);
    Arrays.stream(mainActions).forEach(currentOpenFindingNotification::addAction);
    currentOpenFindingNotification.setImportant(true);
    currentOpenFindingNotification.setIcon(SonarLintIcons.SONARLINT);
    currentOpenFindingNotification.notify(myProject);
  }

  public void expireCurrentFindingNotificationIfNeeded() {
    if (currentOpenFindingNotification != null) {
      currentOpenFindingNotification.expire();
      currentOpenFindingNotification = null;
    }
  }

  public void handle(ShowSmartNotificationParams smartNotificationParams) {
    var connection = ServerConnectionService.getInstance().getServerConnectionByName(smartNotificationParams.getConnectionId());
    if (connection.isEmpty()) {
      GlobalLogOutput.get().log("Connection ID of smart notification should not be null", ClientLogOutput.Level.WARN);
      return;
    }
    boolean isSonarCloud = connection.map(ServerConnection::isSonarCloud).orElse(false);

    String label = isSonarCloud ? "SonarCloud" : "SonarQube";
    var notification = SERVER_NOTIFICATIONS_GROUP.createNotification(
      String.format("<b>%s Notification</b>", label),
      smartNotificationParams.getText(),
      NotificationType.INFORMATION
    );
    if (isSonarCloud) {
      notification.setIcon(SonarLintIcons.ICON_SONARCLOUD_16);
    } else {
      notification.setIcon(SonarLintIcons.ICON_SONARQUBE_16);
    }
    notification.setImportant(true);
    notification.addAction(new OpenInServerAction(label, smartNotificationParams.getLink(), smartNotificationParams.getCategory()));
    notification.addAction(new ConfigureNotificationsAction(connection.get().getName(), myProject));
    notification.notify(myProject);
  }

}
