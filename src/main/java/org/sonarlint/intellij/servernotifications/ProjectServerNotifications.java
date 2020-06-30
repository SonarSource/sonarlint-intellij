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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.messages.MessageBusConnection;
import java.time.ZonedDateTime;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectState;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotification;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotificationListener;

public class ProjectServerNotifications implements StartupActivity {
  private static final NotificationGroup SONARQUBE_GROUP = NotificationGroup.balloonGroup("SonarLint: Server Events");

  @Override
  public void runActivity(@NotNull Project project) {

    EventListener eventListener = new EventListener(project);
    MessageBusConnection busConnection = project.getMessageBus().connect();
    busConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        SonarLintUtils.getService(GlobalServerNotificationsHolder.class).unregister(eventListener);
      }

    });
    SonarLintProjectSettings projectSettings = SonarLintUtils.getService(project, SonarLintProjectSettings.class);
    register(project, projectSettings, eventListener);
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, settings -> {
      // always reset notification date, whether bound or not
      SonarLintProjectState projectState = SonarLintUtils.getService(project, SonarLintProjectState.class);
      projectState.setLastEventPolling(ZonedDateTime.now());
      register(project, settings, eventListener);
    });
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override public void applied(SonarLintGlobalSettings settings) {
        register(project, projectSettings, eventListener);
      }
    });
  }

  private void register(Project project, SonarLintProjectSettings settings, EventListener eventListener) {
    SonarLintUtils.getService(GlobalServerNotificationsHolder.class).unregister(eventListener);
    if (settings.isBindingEnabled()) {
      SonarQubeServer server;
      try {
        ProjectBindingManager bindingManager = SonarLintUtils.getService(project, ProjectBindingManager.class);
        server = bindingManager.getSonarQubeServer();
      } catch (InvalidBindingException e) {
        // do nothing
        return;
      }
      if (server.enableNotifications()) {
        NotificationConfiguration config = createConfiguration(project, settings, server, eventListener);
        SonarLintUtils.getService(GlobalServerNotificationsHolder.class).register(config);
      }
    }
  }

  private static NotificationConfiguration createConfiguration(Project project, SonarLintProjectSettings settings, SonarQubeServer server, EventListener eventListener) {
    String projectKey = settings.getProjectKey();
    ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
    return new NotificationConfiguration(eventListener, new ProjectNotificationTime(project), projectKey, serverConfiguration);
  }

  /**
   * Read and save directly from the mutable object.
   * Any changes in the project settings will affect the next request.
   */
  private static class ProjectNotificationTime implements LastNotificationTime {

    private final Project project;

    ProjectNotificationTime(Project project) {
      this.project = project;
    }

    @Override public ZonedDateTime get() {
      SonarLintProjectState projectState = SonarLintUtils.getService(project, SonarLintProjectState.class);
      ZonedDateTime lastEventPolling = projectState.getLastEventPolling();
      if (lastEventPolling == null) {
        lastEventPolling = ZonedDateTime.now();
        projectState.setLastEventPolling(lastEventPolling);
      }
      return lastEventPolling;
    }

    @Override public void set(ZonedDateTime dateTime) {
      SonarLintProjectState projectState = SonarLintUtils.getService(project, SonarLintProjectState.class);
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
  private static class EventListener implements SonarQubeNotificationListener {

    private final Project project;

    private EventListener(Project project) {
      this.project = project;
    }

    @Override public void handle(SonarQubeNotification notification) {
      Notification notif = SONARQUBE_GROUP.createNotification(
        "<b>SonarQube event</b>",
        createMessage(notification),
        NotificationType.INFORMATION,
        new NotificationListener.UrlOpeningListener(true));
      notif.setImportant(true);
      notif.notify(project);
    }

    private String createMessage(SonarQubeNotification notification) {
      return notification.message() + ".<br/><a href=\"" + notification.link() + "\">Check it here</a>.";
    }
  }

}
