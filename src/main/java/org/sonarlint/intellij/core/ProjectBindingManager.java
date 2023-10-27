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
package org.sonarlint.intellij.core;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.vcs.VcsService;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.ServerConnectionService;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.ProjectBindingListenerKt;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.tasks.BindingStorageUpdateTask;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

import static java.util.Objects.requireNonNull;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isBlank;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

@Service(Service.Level.PROJECT)
public final class ProjectBindingManager {
  private final Project myProject;
  private final ProgressManager progressManager;

  public ProjectBindingManager(Project project) {
    this(project, ProgressManager.getInstance());
  }

  @NonInjectable
  ProjectBindingManager(Project project, ProgressManager progressManager) {
    this.myProject = project;
    this.progressManager = progressManager;
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
    var engineManager = getService(EngineManager.class);
    var projectSettings = getSettingsFor(myProject);
    var notifications = getService(myProject, SonarLintProjectNotifications.class);
    var console = getService(myProject, SonarLintConsole.class);
    if (projectSettings.isBindingEnabled()) {
      var moduleBindingManager = getService(module, ModuleBindingManager.class);
      var connectionId = projectSettings.getConnectionName();
      var projectKey = moduleBindingManager.resolveProjectKey();
      checkBindingStatus(notifications, connectionId, projectKey);
      if (logDetails) {
        console.info(String.format("Using connection '%s' for project '%s'", connectionId, projectKey));
      }
      var engine = engineManager.getConnectedEngine(notifications, connectionId, projectKey);
      return new ConnectedSonarLintFacade(engine, myProject, projectKey);
    }

    return new StandaloneSonarLintFacade(myProject, engineManager.getStandaloneEngine());
  }

  private ConnectedSonarLintEngine getConnectedEngineSkipChecks() {
    var engineManager = getService(EngineManager.class);
    return engineManager.getConnectedEngine(requireNonNull(getSettingsFor(myProject).getConnectionName()));
  }

  public ConnectedSonarLintEngine getConnectedEngine() throws InvalidBindingException {
    var projectSettings = getSettingsFor(myProject);
    if (!projectSettings.isBindingEnabled()) {
      throw new IllegalStateException("Project is not bound to a SonarQube project");
    }
    var notifications = getService(myProject, SonarLintProjectNotifications.class);
    var connectionName = projectSettings.getConnectionName();
    var projectKey = projectSettings.getProjectKey();
    checkBindingStatus(notifications, connectionName, projectKey);

    var engineManager = getService(EngineManager.class);
    return engineManager.getConnectedEngine(notifications, connectionName, projectKey);
  }

  @CheckForNull
  public SonarLintEngine getEngineIfStarted() {
    var engineManager = getService(EngineManager.class);
    var projectSettings = getSettingsFor(myProject);
    if (projectSettings.isBound()) {
      var connectionId = projectSettings.getConnectionName();
      return engineManager.getConnectedEngineIfStarted(requireNonNull(connectionId));
    }
    return engineManager.getStandaloneEngineIfStarted();
  }

  public boolean isBindingValid() {
    return getSettingsFor(myProject).isBound() && tryGetServerConnection().isPresent();
  }

  public @CheckForNull ConnectedSonarLintEngine getValidConnectedEngine() {
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
    var connectionName = getSettingsFor(myProject).getConnectionName();
    var connections = ServerConnectionService.getInstance().getConnections();

    return connections.stream().filter(s -> s.getName().equals(connectionName)).findAny();
  }

  private static void checkBindingStatus(SonarLintProjectNotifications notifications, @Nullable String connectionName, @Nullable String projectKey) throws InvalidBindingException {
    if (connectionName == null) {
      notifications.notifyConnectionIdInvalid();
      throw new InvalidBindingException("Project has an invalid binding");
    } else if (projectKey == null) {
      notifications.notifyProjectStorageInvalid();
      throw new InvalidBindingException("Project has an invalid binding");
    }
  }

  @CheckForNull
  public ProjectBinding getBinding() {
    if (isBindingValid()) {
      var settings = getSettingsFor(myProject);
      return new ProjectBinding(settings.getConnectionName(), settings.getProjectKey(), getModuleOverrides());
    }
    return null;
  }

  public void bindTo(@NotNull ServerConnection connection, @NotNull String projectKey, Map<Module, String> moduleBindingsOverrides) {
    var previousBinding = getBinding();

    var projectSettings = getSettingsFor(myProject);
    projectSettings.bindTo(connection, projectKey);
    moduleBindingsOverrides.forEach((module, overriddenProjectKey) -> getSettingsFor(module).setProjectKey(overriddenProjectKey));
    var modulesToClearOverride = allModules().stream()
      .filter(m -> !moduleBindingsOverrides.containsKey(m));
    unbind(modulesToClearOverride.toList());

    SonarLintProjectNotifications.get(myProject).reset();
    var newBinding = requireNonNull(getBinding());
    if (!Objects.equals(previousBinding, newBinding)) {
      myProject.getMessageBus().syncPublisher(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC()).bindingChanged(previousBinding, newBinding);
      getService(BackendService.class).projectBound(myProject, newBinding);
      var task = new BindingStorageUpdateTask(connection, myProject);
      progressManager.run(task.asModal());
    }
  }

  public void unbind() {
    var previousBinding = getBinding();

    var projectSettings = getSettingsFor(myProject);
    projectSettings.unbind();
    unbind(allModules());

    SonarLintProjectNotifications.get(myProject).reset();
    if (previousBinding != null) {
      myProject.getMessageBus().syncPublisher(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC()).bindingChanged(previousBinding, null);
      getService(BackendService.class).projectUnbound(myProject);
    }
  }

  private static void unbind(List<Module> modules) {
    modules.forEach(m -> getService(m, ModuleBindingManager.class).unbind());
  }

  private List<Module> allModules() {
    return List.of(ModuleManager.getInstance(myProject).getModules());
  }

  public Map<Module, String> getModuleOverrides() {
    return allModules().stream()
      .filter(m -> getSettingsFor(m).isProjectBindingOverridden())
      .collect(Collectors.toMap(m -> m, m -> org.sonarlint.intellij.config.Settings.getSettingsFor(m).getProjectKey()));
  }

  public Set<String> getUniqueProjectKeys() {
    var projectSettings = getSettingsFor(myProject);
    if (projectSettings.isBound()) {
      return getUniqueProjectKeysForModules(allModules());
    }
    return Collections.emptySet();
  }

  public Set<ProjectKeyAndBranch> getUniqueProjectKeysAndBranchesPairs() {
    var projectSettings = getSettingsFor(myProject);
    if (projectSettings.isBound()) {
      VcsService vcsService = getService(myProject, VcsService.class);
      return allModules().stream().map(module -> {
        ModuleBindingManager moduleBindingManager = getService(module, ModuleBindingManager.class);
        var projectKey = moduleBindingManager.resolveProjectKey();
        if (projectKey != null) {
          var branchName = vcsService.getServerBranchName(module);
          return new ProjectKeyAndBranch(projectKey, branchName);
        }
        return null;
      })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    }
    return Set.of();
  }

  public record ProjectKeyAndBranch(String projectKey, @Nullable String branchName) {}

  private static Set<String> getUniqueProjectKeysForModules(Collection<Module> modules) {
    return modules.stream().map(module -> getService(module, ModuleBindingManager.class).resolveProjectKey())
      .filter(projectKey -> !isBlank(projectKey))
      .collect(Collectors.toSet());
  }
}
