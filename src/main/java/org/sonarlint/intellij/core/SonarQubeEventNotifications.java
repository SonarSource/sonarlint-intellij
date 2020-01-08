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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import java.time.ZonedDateTime;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectState;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.notifications.LastNotificationTime;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotification;
import org.sonarsource.sonarlint.core.client.api.notifications.SonarQubeNotificationListener;
import org.sonarsource.sonarlint.core.notifications.SonarQubeNotifications;

public class SonarQubeEventNotifications extends AbstractProjectComponent {
  public static final String GROUP_SONARQUBE_EVENT = "SonarLint: SonarQube Events";

  private final ProjectBindingManager bindingManager;
  private final SonarLintProjectState projectState;
  private final SonarLintProjectSettings projectSettings;
  private final EventListener eventListener;
  private final ProjectNotificationTime notificationTime;
  private final MessageBusConnection busConnection;

  public SonarQubeEventNotifications(Project project, ProjectBindingManager bindingManager, SonarLintProjectState projectState,
    SonarLintProjectSettings projectSettings) {
    super(project);
    this.bindingManager = bindingManager;
    this.projectState = projectState;
    this.projectSettings = projectSettings;
    this.eventListener = new EventListener();
    this.notificationTime = new ProjectNotificationTime();
    this.busConnection = project.getMessageBus().connect(myProject);
  }

  @Override
  public void projectOpened() {
    register(projectSettings);
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, settings -> {
      // always reset notification date, whether bound or not
      projectState.setLastEventPolling(ZonedDateTime.now());
      register(settings);
    });
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override public void applied(SonarLintGlobalSettings settings) {
        register(projectSettings);
      }
    });
  }

  @Override
  public void projectClosed() {
    unregister();
  }

  private void register(SonarLintProjectSettings settings) {
    unregister();
    if (settings.isBindingEnabled()) {
      SonarQubeServer server;
      try {
        server = bindingManager.getSonarQubeServer();
      } catch (InvalidBindingException e) {
        // do nothing
        return;
      }
      if (server.enableNotifications()) {
        NotificationConfiguration config = createConfiguration(settings, server);
        SonarQubeNotifications.get().register(config);
      }
    }
  }

  private void unregister() {
    SonarQubeNotifications.get().remove(eventListener);
  }

  private NotificationConfiguration createConfiguration(SonarLintProjectSettings settings, SonarQubeServer server) {
    String projectKey = settings.getProjectKey();
    ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
    return new NotificationConfiguration(eventListener, notificationTime, projectKey, serverConfiguration);
  }

  /**
   * Read and save directly from the mutable object.
   * Any changes in the project settings will affect the next request.
   */
  private class ProjectNotificationTime implements LastNotificationTime {

    @Override public ZonedDateTime get() {
      ZonedDateTime lastEventPolling = projectState.getLastEventPolling();
      if (lastEventPolling == null) {
        lastEventPolling = ZonedDateTime.now();
        projectState.setLastEventPolling(lastEventPolling);
      }
      return lastEventPolling;
    }

    @Override public void set(ZonedDateTime dateTime) {
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
  private class EventListener implements SonarQubeNotificationListener {
    @Override public void handle(SonarQubeNotification notification) {
      Notification notif = new Notification(GROUP_SONARQUBE_EVENT,
        "<b>SonarQube event</b>",
        createMessage(notification),
        NotificationType.INFORMATION,
        new NotificationListener.UrlOpeningListener(true));
      notif.setImportant(true);
      notif.notify(myProject);
    }

    private String createMessage(SonarQubeNotification notification) {
      return notification.message() + ".<br/><a href=\"" + notification.link() + "\">Check it here</a>.";
    }
  }
}
