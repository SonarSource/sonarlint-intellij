/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.intellij.openapi.project.Project;
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
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.ProjectBindingListenerKt;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;

import static java.util.Objects.requireNonNull;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isBlank;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

@Service(Service.Level.PROJECT)
public final class ProjectBindingManager {
  private final Project myProject;

  public ProjectBindingManager(Project project) {
    myProject = project;
  }

  /**
   * Returns a Facade with the appropriate engine (standalone or connected) based on the current project and module configurations.
   * In case of a problem, it handles the displaying of errors (Logging, user notifications, ..) and throws an InvalidBindingException.
   *
   * @throws InvalidBindingException If current project binding is invalid
   */
  public EngineFacade getFacade(Module module) throws InvalidBindingException {
    var engineManager = getService(EngineManager.class);
    var projectSettings = getSettingsFor(myProject);
    var notifications = getService(myProject, SonarLintProjectNotifications.class);
    if (projectSettings.isBindingEnabled()) {
      var moduleBindingManager = getService(module, ModuleBindingManager.class);
      var connectionId = projectSettings.getConnectionName();
      var projectKey = moduleBindingManager.resolveProjectKey();
      checkBindingStatus(notifications, connectionId, projectKey);
      var engine = engineManager.getConnectedEngine(notifications, requireNonNull(connectionId));
      return new EngineFacade(myProject, engine);
    }

    return new EngineFacade(myProject, engineManager.getStandaloneEngine());
  }

  @CheckForNull
  public SonarLintAnalysisEngine getEngineIfStarted() {
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

  public ServerConnection getServerConnection() throws InvalidBindingException {
    return tryGetServerConnection().orElseThrow(
      () -> new InvalidBindingException("Unable to find a connection with name: " + getSettingsFor(myProject).getConnectionName()));
  }

  public Optional<ServerConnection> tryGetServerConnection() {
    if (!getSettingsFor(myProject).isBindingEnabled()) {
      return Optional.empty();
    }
    var connectionName = getSettingsFor(myProject).getConnectionName();
    var connections = getGlobalSettings().getServerConnections();

    var connectionFound = connections.stream().filter(s -> s.getName().equals(connectionName)).findAny();
    if (connectionFound.isEmpty()) {
      var notifications = getService(myProject, SonarLintProjectNotifications.class);
      notifications.notifyProjectBindingInvalidAndUnbound();
      getSettingsFor(myProject).unbind();
      return Optional.empty();
    } else {
      return connectionFound;
    }
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

    SonarLintProjectNotifications.Companion.get(myProject).reset();
    var newBinding = requireNonNull(getBinding());
    if (!Objects.equals(previousBinding, newBinding)) {
      myProject.getMessageBus().syncPublisher(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC()).bindingChanged(previousBinding, newBinding);
      getService(BackendService.class).projectBound(myProject, newBinding);
    }
  }

  public void unbind() {
    var previousBinding = getBinding();

    var projectSettings = getSettingsFor(myProject);
    projectSettings.unbind();
    unbind(allModules());

    SonarLintProjectNotifications.Companion.get(myProject).reset();
    if (previousBinding != null) {
      myProject.getMessageBus().syncPublisher(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC()).bindingChanged(previousBinding, null);
      getService(BackendService.class).projectUnbound(myProject);
    }
  }

  private static void unbind(List<Module> modules) {
    runOnPooledThread(() -> modules.forEach(m -> getService(m, ModuleBindingManager.class).unbind()));
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

  private static Set<String> getUniqueProjectKeysForModules(Collection<Module> modules) {
    return modules.stream().map(module -> getService(module, ModuleBindingManager.class).resolveProjectKey())
      .filter(projectKey -> !isBlank(projectKey))
      .collect(Collectors.toSet());
  }
}
