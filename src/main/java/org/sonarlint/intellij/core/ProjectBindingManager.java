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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.messages.ProjectEngineListener;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.tasks.BindingStorageUpdateTask;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

import static java.util.Objects.requireNonNull;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ProjectBindingManager {
  private final Project myProject;
  private final Supplier<SonarLintEngineManager> engineManagerSupplier;

  public ProjectBindingManager(Project project) {
    this(project, () -> SonarLintUtils.getService(SonarLintEngineManager.class));
  }

  @NonInjectable
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
  public SonarLintFacade getFacade(Module module) throws InvalidBindingException {
    return getFacade(module, false);
  }

  public SonarLintFacade getFacade(Module module, boolean logDetails) throws InvalidBindingException {
    SonarLintEngineManager engineManager = this.engineManagerSupplier.get();
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    SonarLintProjectNotifications notifications = SonarLintUtils.getService(myProject, SonarLintProjectNotifications.class);
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    if (projectSettings.isBindingEnabled()) {
      ModuleBindingManager moduleBindingManager = SonarLintUtils.getService(module, ModuleBindingManager.class);
      String connectionId = projectSettings.getConnectionName();
      String projectKey = moduleBindingManager.resolveProjectKey();
      checkBindingStatus(notifications, connectionId, projectKey);
      if (logDetails) {
        console.info(String.format("Using connection '%s' for project '%s'", connectionId, projectKey));
      }
      ConnectedSonarLintEngine engine = engineManager.getConnectedEngine(notifications, connectionId, projectKey);
      return new ConnectedSonarLintFacade(engine, myProject, projectKey);
    }

    return new StandaloneSonarLintFacade(myProject, engineManager.getStandaloneEngine());
  }

  private ConnectedSonarLintEngine getConnectedEngineSkipChecks() {
    SonarLintEngineManager engineManager = this.engineManagerSupplier.get();
    return engineManager.getConnectedEngine(requireNonNull(getSettingsFor(myProject).getConnectionName()));
  }

  public ConnectedSonarLintEngine getConnectedEngine() throws InvalidBindingException {
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

  @CheckForNull
  public SonarLintEngine getEngineIfStarted() {
    SonarLintEngineManager engineManager = this.engineManagerSupplier.get();
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    if (projectSettings.isBound()) {
      String connectionId = projectSettings.getConnectionName();
      return engineManager.getConnectedEngineIfStarted(requireNonNull(connectionId));
    }
    return engineManager.getStandaloneEngineIfStarted();
  }

  public boolean isBindingValid() {
    return getSettingsFor(myProject).isBound() && tryGetServerConnection().isPresent();
  }

  public @Nullable ConnectedSonarLintEngine getValidConnectedEngine() {
    return isBindingValid() ? getConnectedEngineSkipChecks() : null;
  }

  public ServerConnection getServerConnection() throws InvalidBindingException {
    return tryGetServerConnection().orElseThrow(
      () -> new InvalidBindingException("Unable to find a connection with name: " + getSettingsFor(myProject).getConnectionName()));
  }

  public Optional<ServerConnection> tryGetServerConnection() {
    if (!getSettingsFor(myProject).isBindingEnabled()) {
      return Optional.empty();
    }
    String connectionName = getSettingsFor(myProject).getConnectionName();
    List<ServerConnection> connections = getGlobalSettings().getServerConnections();

    return connections.stream().filter(s -> s.getName().equals(connectionName)).findAny();
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

  public void bindTo(@NotNull ServerConnection connection, @NotNull String projectKey, Map<Module, String> moduleBindingsOverrides) {
    SonarLintEngine previousEngine = getEngineIfStarted();
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    projectSettings.bindTo(connection, projectKey);
    moduleBindingsOverrides.entrySet().forEach(e -> {
      SonarLintModuleSettings moduleSettings = getSettingsFor(e.getKey());
      moduleSettings.overrideProjectBinding(e.getValue());
    });

    Module[] allModules = ModuleManager.getInstance(myProject).getModules();
    Stream<Module> modulesToClearOverride = Stream.of(allModules)
      .filter(m -> !moduleBindingsOverrides.containsKey(m));
    modulesToClearOverride.forEach(m -> {
      SonarLintModuleSettings moduleSettings = getSettingsFor(m);
      moduleSettings.clearBindingOverride();
    });

    SonarLintProjectNotifications.get(myProject).reset();
    ConnectedSonarLintEngine newEngine = getConnectedEngineSkipChecks();
    if (previousEngine != newEngine) {
      myProject.getMessageBus().syncPublisher(ProjectEngineListener.TOPIC).engineChanged(previousEngine, newEngine);
    }
    BindingStorageUpdateTask task = new BindingStorageUpdateTask(newEngine, connection, false, true, myProject);
    ProgressManager.getInstance().run(task.asModal());
    myProject.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC).changed(projectSettings);
  }

  public void unbind() {
    SonarLintEngine previousEngine = getEngineIfStarted();
    SonarLintProjectSettings projectSettings = getSettingsFor(myProject);
    projectSettings.unbind();
    SonarLintProjectNotifications.get(myProject).reset();
    ProjectConfigurationListener projectListener = myProject.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
    projectListener.changed(projectSettings);
    SonarLintEngine standaloneEngine = getEngineIfStarted();
    if (previousEngine != standaloneEngine) {
      myProject.getMessageBus().syncPublisher(ProjectEngineListener.TOPIC).engineChanged(previousEngine, standaloneEngine);
    }
  }

  public Map<Module, SonarLintModuleSettings> getModuleOverrides() {
    return Stream.of(ModuleManager.getInstance(myProject).getModules())
      .filter(m -> getSettingsFor(m).isProjectBindingOverridden())
      .collect(Collectors.toMap(m -> m, org.sonarlint.intellij.config.Settings::getSettingsFor));
  }
}
