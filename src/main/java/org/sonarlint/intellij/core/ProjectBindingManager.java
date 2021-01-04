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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.tasks.BindingStorageUpdateTask;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ProjectBindingManager {
  private final Project myProject;
  private final Supplier<SonarLintEngineManager> engineManagerSupplier;

  public ProjectBindingManager(Project project) {
    this(project, () -> SonarLintUtils.getService(SonarLintEngineManager.class));
  }

  /**
   * TODO Replace @Deprecated with @NonInjectable when switching to 2019.3 API level
   * @deprecated in 4.2 to silence a check in 2019.3
   */
  @Deprecated
  ProjectBindingManager(Project project, Supplier<SonarLintEngineManager> engineManagerSupplier) {
    this.myProject = project;
    this.engineManagerSupplier = engineManagerSupplier;
  }

  /**
   * Returns a Facade with the appropriate engine (standalone or connected) based on the current project and module configurations.
   * In case of a problem, it handles the displaying of errors (Logging, user notifications, ..) and throws an InvalidBindingException.
   *
   * @throws InvalidBindingException If current project binding is invalid
   */
  public synchronized SonarLintFacade getFacade() throws InvalidBindingException {
    return getFacade(false);
  }

  public synchronized SonarLintFacade getFacade(boolean logDetails) throws InvalidBindingException {
    SonarLintEngineManager engineManager = this.engineManagerSupplier.get();
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    SonarLintProjectNotifications notifications = SonarLintUtils.getService(myProject, SonarLintProjectNotifications.class);
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    if (projectSettings.isBindingEnabled()) {
      String connectionId = projectSettings.getConnectionName();
      String projectKey = projectSettings.getProjectKey();
      checkBindingStatus(notifications, connectionId, projectKey);
      if (logDetails) {
        console.info(String.format("Using connection '%s' for project '%s'", connectionId, projectKey));
      }
      ConnectedSonarLintEngine engine = engineManager.getConnectedEngine(notifications, connectionId, projectKey);
      return new ConnectedSonarLintFacade(connectionId, engine, myProject);
    }

    return new StandaloneSonarLintFacade(myProject, engineManager.getStandaloneEngine());
  }

  public synchronized ConnectedSonarLintEngine getConnectedEngineSkipChecks() {
    SonarLintEngineManager engineManager = this.engineManagerSupplier.get();
    return engineManager.getConnectedEngine(getSettingsFor(myProject).getConnectionName());
  }

  public synchronized ConnectedSonarLintEngine getConnectedEngine() throws InvalidBindingException {
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    if (!projectSettings.isBindingEnabled()) {
      throw new IllegalStateException("Project is not bound to a SonarQube project");
    }
    SonarLintProjectNotifications notifications = SonarLintUtils.getService(myProject, SonarLintProjectNotifications.class);
    String connectionName = projectSettings.getConnectionName();
    String projectKey = projectSettings.getProjectKey();
    checkBindingStatus(notifications, connectionName, projectKey);

    SonarLintEngineManager engineManager = this.engineManagerSupplier.get();
    return engineManager.getConnectedEngine(notifications, connectionName, projectKey);
  }

  public synchronized ServerConnection getServerConnection() throws InvalidBindingException {
    String connectionName = getSettingsFor(myProject).getConnectionName();
    List<ServerConnection> connections = getGlobalSettings().getServerConnections();

    Optional<ServerConnection> connection = connections.stream().filter(s -> s.getName().equals(connectionName)).findAny();
    return connection.orElseThrow(() -> new InvalidBindingException("Unable to find a connection with name: " + connectionName));
  }

  private static void checkBindingStatus(SonarLintProjectNotifications notifications, @Nullable String connectionName, @Nullable String projectKey) throws InvalidBindingException {
    if (connectionName == null) {
      notifications.notifyConnectionIdInvalid();
      throw new InvalidBindingException("Project has an invalid binding");
    } else if (projectKey == null) {
      notifications.notifyModuleInvalid();
      throw new InvalidBindingException("Project has an invalid binding");
    }
  }

  public void bindTo(@NotNull ServerConnection connection, @NotNull String projectKey) {
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    projectSettings.bindTo(connection, projectKey);
    SonarLintProjectNotifications.get(myProject).reset();
    BindingStorageUpdateTask task = new BindingStorageUpdateTask(getConnectedEngineSkipChecks(), connection,
      Collections.singletonMap(projectKey, Collections.singletonList(myProject)), true);
    ProgressManager.getInstance().run(task.asModal());
    myProject.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC).changed(projectSettings);
  }

  public void unbind() {
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    projectSettings.unbind();
    SonarLintProjectNotifications.get(myProject).reset();
    ProjectConfigurationListener projectListener = myProject.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
    projectListener.changed(projectSettings);
  }
}
