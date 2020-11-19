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
package org.sonarlint.intellij.core;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import icons.SonarLintIcons;
import java.time.ZonedDateTime;
import java.util.Optional;
import javax.swing.JFrame;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectState;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotificationListener;
import org.sonarsource.sonarlint.core.notifications.ServerNotifications;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ProjectServerNotifications {
  private static final NotificationGroup SERVER_NOTIFICATIONS_GROUP =
    new NotificationGroup("SonarLint: Server Notifications", NotificationDisplayType.STICKY_BALLOON, true, "SonarLint");
  private EventListener eventListener;
  private final ProjectNotificationTime notificationTime;
  private final MessageBusConnection busConnection;
  private final Project myProject;

  public ProjectServerNotifications(Project project) {
    myProject = project;
    this.notificationTime = new ProjectNotificationTime();
    this.busConnection = project.getMessageBus().connect(myProject);
  }

  public void init() {
    register();
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, settings -> {
      // always reset notification date, whether bound or not
      SonarLintProjectState projectState = SonarLintUtils.getService(myProject, SonarLintProjectState.class);
      projectState.setLastEventPolling(ZonedDateTime.now());
      register();
    });
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override
      public void applied(SonarLintGlobalSettings settings) {
        register();
      }
    });
  }


  private void register() {
    SonarLintProjectSettings settings = getSettingsFor(myProject);
    unregister();
    if (settings.isBindingEnabled()) {
      ServerConnection connection;
      try {
        ProjectBindingManager bindingManager = SonarLintUtils.getService(myProject, ProjectBindingManager.class);
        connection = bindingManager.getServerConnection();
      } catch (InvalidBindingException e) {
        // do nothing
        return;
      }
      if (!connection.isDisableNotifications()) {
        this.eventListener = new EventListener(connection.isSonarCloud(), connection.getName());
        NotificationConfiguration config = createConfiguration(settings, connection);
        if (ServerNotifications.get().isSupported(config.serverConfiguration().get())) {
          ServerNotificationsFacade.get().register(config);
        }
      }
    }
  }

  public void unregister() {
    if (eventListener != null) {
      ServerNotificationsFacade.get().unregister(eventListener);
      eventListener = null;
    }
  }

  private NotificationConfiguration createConfiguration(SonarLintProjectSettings settings, ServerConnection server) {
    String projectKey = settings.getProjectKey();
    return new NotificationConfiguration(eventListener, notificationTime, projectKey, () -> SonarLintUtils.getServerConfiguration(server));
  }

  /**
   * Read and save directly from the mutable object.
   * Any changes in the project settings will affect the next request.
   */
  private class ProjectNotificationTime implements LastNotificationTime {

    @Override
    public ZonedDateTime get() {
      SonarLintProjectState projectState = SonarLintUtils.getService(myProject, SonarLintProjectState.class);
      ZonedDateTime lastEventPolling = projectState.getLastEventPolling();
      if (lastEventPolling == null) {
        lastEventPolling = ZonedDateTime.now();
        projectState.setLastEventPolling(lastEventPolling);
      }
      return lastEventPolling;
    }

    @Override
    public void set(ZonedDateTime dateTime) {
      SonarLintProjectState projectState = SonarLintUtils.getService(myProject, SonarLintProjectState.class);
      ZonedDateTime lastEventPolling = projectState.getLastEventPolling();
      if (lastEventPolling != null && dateTime.isBefore(lastEventPolling)) {
        // this can happen if the settings changed between the read and write
        return;
      }
      projectState.setLastEventPolling(dateTime);
    }
  }

  /**
   * Simply displays the events and discards it
   */
  private class EventListener implements ServerNotificationListener {

    private final boolean isSonarCloud;
    private final String connectionName;

    EventListener(boolean isSonarCloud, String connectionName) {
      this.isSonarCloud = isSonarCloud;
      this.connectionName = connectionName;
    }

    @Override
    public void handle(ServerNotification serverNotification) {
      SonarLintTelemetry telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
      final String category = serverNotification.category();
      telemetry.devNotificationsReceived(category);
      final String label = isSonarCloud ? "SonarCloud" : "SonarQube";
      Notification notif = SERVER_NOTIFICATIONS_GROUP.createNotification(
        "<b>" + label + " Notification</b>",
        serverNotification.message(),
        NotificationType.INFORMATION,
        null);
      notif.setIcon(isSonarCloud ? SonarLintIcons.ICON_SONARCLOUD_16 : SonarLintIcons.ICON_SONARQUBE_16);
      notif.setImportant(true);
      notif.addAction(new OpenInServerAction(label, serverNotification.link(), category));
      notif.addAction(new ConfigureNotificationsAction(connectionName));
      notif.notify(myProject);
    }
  }

  private static class OpenInServerAction extends NotificationAction {

    private final String link;
    private final String category;

    private OpenInServerAction(String serverLabel, String link, String category) {
      super("Open in " + serverLabel);
      this.link = link;
      this.category = category;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      SonarLintTelemetry telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
      telemetry.devNotificationsClicked(category);
      BrowserUtil.browse(link);
      notification.hideBalloon();
    }
  }

  private static class ConfigureNotificationsAction extends NotificationAction {

    private final String connectionName;

    private ConfigureNotificationsAction(String connectionName) {
      super("Configure");
      this.connectionName = connectionName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      final JFrame parent = WindowManager.getInstance().getFrame(e.getProject());
      if (parent == null) {
        return;
      }
      UIUtil.invokeLaterIfNeeded(() -> {
        final Optional<ServerConnection> connection = getGlobalSettings().getServerConnections().stream().filter(s -> s.getName().equals(connectionName)).findAny();
        if (connection.isPresent()) {
          SonarLintGlobalConfigurable globalConfigurable = new SonarLintGlobalConfigurable();
          ShowSettingsUtil.getInstance().editConfigurable(parent, globalConfigurable, () -> globalConfigurable.editNotifications(connection.get()));
        } else if (e.getProject() != null) {
          SonarLintConsole.get(e.getProject()).error("Unable to find connection with name: " + connectionName);
          notification.expire();
        }
      });
    }
  }
}
