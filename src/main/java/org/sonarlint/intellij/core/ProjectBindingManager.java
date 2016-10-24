/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

public class ProjectBindingManager extends AbstractProjectComponent {
  private final SonarLintEngineManager engineManager;
  private final SonarLintProjectSettings projectSettings;
  private final SonarLintGlobalSettings globalSettings;
  private final SonarLintProjectNotifications notifications;
  private final SonarLintConsole console;

  public ProjectBindingManager(Project project, SonarLintEngineManager engineManager, SonarLintProjectSettings projectSettings,
    SonarLintGlobalSettings globalSettings, SonarLintProjectNotifications notifications, SonarLintConsole console) {
    super(project);
    this.engineManager = engineManager;
    this.projectSettings = projectSettings;
    this.globalSettings = globalSettings;
    this.notifications = notifications;
    this.console = console;
  }

  /**
   * Will create a Facade with the appropriate engine (standalone or connected) based on the current project and module configurations.
   * In case of a problem, it handles the displaying of errors (Logging, user notifications, ..) and throws an IllegalStateException.
   */
  public synchronized SonarLintFacade getFacadeForAnalysis() {
    if (projectSettings.isBindingEnabled()) {
      String serverId = projectSettings.getServerId();
      String projectKey = projectSettings.getProjectKey();
      checkBindingStatus(notifications, serverId, projectKey);
      console.info(String.format("Using configuration of '%s' in server '%s'", projectKey, serverId));

      ConnectedSonarLintEngine engine = engineManager.getConnectedEngine(notifications, serverId, projectKey);
      return new ConnectedSonarLintFacade(engine, projectSettings, console, myProject, projectKey);
    }
    return new StandaloneSonarLintFacade(projectSettings, console, myProject, engineManager.getStandaloneEngine());
  }

  public synchronized ConnectedSonarLintEngine getConnectedEngine() {
    if (!projectSettings.isBindingEnabled()) {
      throw new IllegalStateException("Project is not bound to a SonarQube project");
    }

    String serverId = projectSettings.getServerId();
    String projectKey = projectSettings.getProjectKey();
    checkBindingStatus(notifications, serverId, projectKey);

    return engineManager.getConnectedEngine(notifications, serverId, projectKey);
  }

  public synchronized SonarQubeServer getSonarQubeServer() {
    String serverId = projectSettings.getServerId();
    List<SonarQubeServer> servers = globalSettings.getSonarQubeServers();

    Optional<SonarQubeServer> server = servers.stream().filter(s -> s.getName().equals(serverId)).findAny();
    return server.orElseThrow(() -> new IllegalStateException("SonarQube server configuration does not exist for server id: " + serverId));
  }

  private static void checkBindingStatus(SonarLintProjectNotifications notifications, @Nullable String serverId, @Nullable String projectKey) {
    if (serverId == null) {
      notifications.notifyServerIdInvalid();
      throw new IllegalStateException("Project has an invalid binding");
    } else if (projectKey == null) {
      notifications.notifyModuleInvalid();
      throw new IllegalStateException("Project has an invalid binding");
    }
  }
}
