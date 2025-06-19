/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.ProjectBindingListenerKt;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;

import static java.util.Objects.requireNonNull;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

@Service(Service.Level.PROJECT)
public final class ProjectBindingManager {
  private final Project myProject;

  private static final String SKIP_SHARED_CONFIGURATION_DIALOG_PROPERTY = "SonarLint.shareConfiguration";

  public ProjectBindingManager(Project project) {
    myProject = project;
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

  @CheckForNull
  public ProjectBinding getBinding() {
    if (isBindingValid()) {
      var settings = getSettingsFor(myProject);
      return new ProjectBinding(settings.getConnectionName(), settings.getProjectKey(), getModuleOverrides());
    }
    return null;
  }

  public void bindTo(@NotNull ServerConnection connection, @NotNull String projectKey, Map<Module, String> moduleBindingsOverrides,
    BindingMode bindingMode) {
    var previousBinding = getProjectBinding(connection, projectKey, moduleBindingsOverrides);

    SonarLintProjectNotifications.Companion.get(myProject).reset();
    var newBinding = requireNonNull(getBinding());
    if (!Objects.equals(previousBinding, newBinding)) {
      myProject.getMessageBus().syncPublisher(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC()).bindingChanged();
      updateTelemetryOnBind(bindingMode);
      getService(BackendService.class).projectBound(myProject, newBinding);
    }
  }

  public void bindToManually(@NotNull ServerConnection connection, @NotNull String projectKey, Map<Module, String> moduleBindingsOverrides) {
    var previousBinding = getProjectBinding(connection, projectKey, moduleBindingsOverrides);

    SonarLintProjectNotifications.Companion.get(myProject).reset();
    var newBinding = requireNonNull(getBinding());

    if (!Objects.equals(previousBinding, newBinding)) {
      myProject.getMessageBus().syncPublisher(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC()).bindingChanged();
      updateTelemetryOnBind(BindingMode.MANUAL);
      getService(BackendService.class).projectBound(myProject, newBinding);

      showSharedConfigurationNotification(myProject, String.format("""
        Project successfully bound with '%s' on '%s'.
        When sharing this configuration, a file will be created in this working directory,
        making it easier for other team members to configure the binding for the same project.
        This configuration may also be shared later from your list of bound projects
        """, newBinding.getProjectKey(), newBinding.getConnectionName())
      );

    }
  }

  @Nullable
  private ProjectBinding getProjectBinding(@NotNull ServerConnection connection, @NotNull String projectKey, Map<Module, String> moduleBindingsOverrides) {
    var previousBinding = getBinding();

    var projectSettings = getSettingsFor(myProject);
    projectSettings.bindTo(connection, projectKey);
    moduleBindingsOverrides.forEach((module, overriddenProjectKey) -> getSettingsFor(module).setProjectKey(overriddenProjectKey));
    var modulesToClearOverride = allModules().stream()
      .filter(m -> !moduleBindingsOverrides.containsKey(m));
    unbind(modulesToClearOverride.toList());
    return previousBinding;
  }

  private static void showSharedConfigurationNotification(Project project, String message) {
    if (!PropertiesComponent.getInstance().getBoolean(SKIP_SHARED_CONFIGURATION_DIALOG_PROPERTY)) {
      SonarLintProjectNotifications.Companion.get(project).showSharedConfigurationNotification("Project successfully bound",
        message, SKIP_SHARED_CONFIGURATION_DIALOG_PROPERTY);
    }
  }

  public void unbind() {
    var previousBinding = getBinding();

    var projectSettings = getSettingsFor(myProject);
    projectSettings.unbind();
    unbind(allModules());

    SonarLintProjectNotifications.Companion.get(myProject).reset();
    if (previousBinding != null) {
      runOnPooledThread(myProject, () -> {
        myProject.getMessageBus().syncPublisher(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC()).bindingChanged();
        getService(BackendService.class).projectUnbound(myProject);
      });
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

  public static void updateTelemetryOnBind(BindingMode bindingMode) {
    switch (bindingMode) {
      case AUTOMATIC -> getService(SonarLintTelemetry.class).addedAutomaticBindings();
      case IMPORTED -> getService(SonarLintTelemetry.class).addedImportedBindings();
      case MANUAL -> getService(SonarLintTelemetry.class).addedManualBindings();
    }
  }

  public enum BindingMode {
    AUTOMATIC,
    IMPORTED,
    MANUAL
  }

}
