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

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import icons.SonarLintIcons;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import javax.swing.JFrame;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.wizard.ServerConnectionWizard;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectState;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.ui.BalloonNotifier;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification;
import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotificationListener;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.util.SonarLintUtils.getService;

public class ProjectServerNotificationsSubscriber implements Disposable {
  private static final NotificationGroup SERVER_NOTIFICATIONS_GROUP =
    new NotificationGroup("SonarLint: Server Notifications", NotificationDisplayType.STICKY_BALLOON, true, "SonarLint");
  private EventListener eventListener;
  private final ProjectNotificationTime notificationTime;
  private final Project myProject;
  private final ServerNotificationsService notificationsService;

  public ProjectServerNotificationsSubscriber(Project project) {
    this(project, ServerNotificationsService.get());
  }

  ProjectServerNotificationsSubscriber(Project project, ServerNotificationsService notificationsService) {
    myProject = project;
    this.notificationsService = notificationsService;
    this.notificationTime = new ProjectNotificationTime();
  }

  public void start() {
    register();
    MessageBusConnection busConnection = myProject.getMessageBus().connect();
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, settings -> {
      // always reset notification date, whether bound or not
      SonarLintProjectState projectState = getService(myProject, SonarLintProjectState.class);
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
        ProjectBindingManager bindingManager = getService(myProject, ProjectBindingManager.class);
        connection = bindingManager.getServerConnection();
      } catch (InvalidBindingException e) {
        // do nothing
        return;
      }
      if (!connection.isDisableNotifications()) {
        this.eventListener = new EventListener(connection.isSonarCloud(), connection.getName());
        NotificationConfiguration config = createConfiguration(settings, connection);
        if (notificationsService.isSupported(config.serverConfiguration().get())) {
          notificationsService.register(config);
        }
      }
    }
  }

  @Override
  public void dispose() {
    unregister();
  }

  private void unregister() {
    if (eventListener != null) {
      notificationsService.unregister(eventListener);
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
      SonarLintProjectState projectState = getService(myProject, SonarLintProjectState.class);
      ZonedDateTime lastEventPolling = projectState.getLastEventPolling();
      if (lastEventPolling == null) {
        lastEventPolling = ZonedDateTime.now();
        projectState.setLastEventPolling(lastEventPolling);
      }
      return lastEventPolling;
    }

    @Override
    public void set(ZonedDateTime dateTime) {
      SonarLintProjectState projectState = getService(myProject, SonarLintProjectState.class);
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
      SonarLintTelemetry telemetry = getService(SonarLintTelemetry.class);
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
      getService(myProject, BalloonNotifier.class).show(notif);
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
      SonarLintTelemetry telemetry = getService(SonarLintTelemetry.class);
      telemetry.devNotificationsClicked(category);
      BrowserUtil.browse(link);
      notification.hideBalloon();
    }
  }

  private class ConfigureNotificationsAction extends NotificationAction {

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
        final Optional<ServerConnection> connectionToEditOpt = getGlobalSettings().getServerConnections().stream().filter(s -> s.getName().equals(connectionName)).findAny();
        if (connectionToEditOpt.isPresent()) {
          final ServerConnection connectionToEdit = connectionToEditOpt.get();
          final ServerConnectionWizard wizard = ServerConnectionWizard.forNotificationsEdition(connectionToEdit);
          if (wizard.showAndGet()) {
            final ServerConnection editedConnection = wizard.getConnection();
            final List<ServerConnection> serverConnections = getGlobalSettings().getServerConnections();
            serverConnections.set(serverConnections.indexOf(connectionToEdit), editedConnection);
            register();
          }
        } else if (e.getProject() != null) {
          SonarLintConsole.get(e.getProject()).error("Unable to find connection with name: " + connectionName);
          notification.expire();
        }
      });
    }
  }
}
