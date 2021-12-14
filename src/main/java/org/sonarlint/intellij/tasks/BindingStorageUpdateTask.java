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
package org.sonarlint.intellij.tasks;

import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.Topic;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ModuleBindingManager;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;

import static java.util.stream.Collectors.toSet;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class BindingStorageUpdateTask {
  private final ConnectedSonarLintEngine engine;
  private final ServerConnection connection;
  private final boolean updateGlobalStorage;
  private final boolean updateProjectsStorage;
  private final Project onlyForProject;

  public BindingStorageUpdateTask(ConnectedSonarLintEngine engine, ServerConnection connection, boolean updateGlobalStorage, boolean updateProjectsStorage,
    @Nullable Project onlyForProject) {
    this.engine = engine;
    this.connection = connection;
    this.updateGlobalStorage = updateGlobalStorage;
    this.updateProjectsStorage = updateProjectsStorage;
    this.onlyForProject = onlyForProject;
  }

  public Task.Modal asModal() {
    return new Task.Modal(null, "Updating storage for connection '" + connection.getName() + "'", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        BindingStorageUpdateTask.this.run(indicator);
      }
    };
  }

  public Task.Backgroundable asBackground() {
    return new Task.Backgroundable(null, "Updating storage for connection '" + connection.getName() + "'", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        BindingStorageUpdateTask.this.run(indicator);
      }
    };
  }

  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Update in progress...");

    try {
      indicator.setIndeterminate(false);
      var monitor = new TaskProgressMonitor(indicator, null);

      var connectedModeEndpoint = connection.getEndpointParams();
      if (updateGlobalStorage) {
        engine.update(connectedModeEndpoint, connection.getHttpClient(), monitor);
        GlobalLogOutput.get().log("Storage for connection '" + connection.getName() + "' updated", ClientLogOutput.Level.INFO);
      }

      if (updateProjectsStorage) {
        updateProjectStorages(connection, monitor);
      }

    } catch (CanceledException e) {
      GlobalLogOutput.get().log("Update of storage for connection '" + connection.getName() + "' was cancelled", ClientLogOutput.Level.INFO);
    } catch (Exception e) {
      GlobalLogOutput.get().logError("Error updating the storage for connection '" + connection.getName() + "'", e);
      final var msg = (e.getMessage() != null) ? e.getMessage() : ("Failed to update the binding for connection '" + connection.getName() + "'");
      ApplicationManager.getApplication().invokeAndWait(new RunnableAdapter() {
        @Override
        public void doRun() {
          Messages.showErrorDialog((Project) null, msg, "Binding Update Failed");
        }
      }, ModalityState.any());
    }
  }

  /**
   * Updates all known projects belonging to a connection. Except if project class attribute is provided.
   * It assumes that the global storage was updated previously.
   */
  private void updateProjectStorages(ServerConnection connection, TaskProgressMonitor monitor) {
    var projectsToUpdate = collectProjectsToUpdate(connection);

    var failures = tryUpdateProjectStorages(connection, monitor, projectsToUpdate);

    projectsToUpdate.forEach(this::updatePathPrefixesForAllModules);
    projectsToUpdate.forEach(BindingStorageUpdateTask::analyzeOpenFiles);
    projectsToUpdate.forEach(BindingStorageUpdateTask::notifyBindingStorageUpdated);

    if (!failures.isEmpty()) {
      var errorMsg = "Failed to update the storage for some projects: \n"
        + failures.stream().map(f -> " - " + f.getReason().getMessage() + " (" + f.getProjectKey() + ")").collect(Collectors.joining("\n"));

      ApplicationManager.getApplication().invokeLater(new RunnableAdapter() {
        @Override
        public void doRun() {
          Messages.showWarningDialog((Project) null, errorMsg, "Projects Not Updated");
        }
      }, ModalityState.any());
    }
  }

  private List<ProjectStorageUpdateFailure> tryUpdateProjectStorages(ServerConnection connection, TaskProgressMonitor monitor, Collection<Project> projectsToUpdate) {
    var failures = new ArrayList<ProjectStorageUpdateFailure>();

    var projectKeysToUpdate = projectsToUpdate.stream()
      .flatMap(p -> getService(p, ProjectBindingManager.class).getUniqueProjectKeys()
        .stream())
      .collect(toSet());

    projectKeysToUpdate.forEach(projectKeyFromModule -> {
      try {
        engine.updateProject(connection.getEndpointParams(), connection.getHttpClient(), projectKeyFromModule, true, monitor);
      } catch (Exception e) {
        GlobalLogOutput.get().logError(e.getMessage(), e);
        failures.add(new ProjectStorageUpdateFailure(projectKeyFromModule, e));
      }
    });

    engine.sync(connection.getEndpointParams(), connection.getHttpClient(), projectKeysToUpdate, monitor);

    return failures;
  }

  private Collection<Project> collectProjectsToUpdate(ServerConnection connection) {
    return onlyForProject != null ?
      Collections.singleton(onlyForProject) :
      Stream.of(ProjectManager.getInstance().getOpenProjects())
        .filter(p -> getSettingsFor(p).isBoundTo(connection))
        .collect(Collectors.toList());
  }

  private static void notifyBindingStorageUpdated(Project project) {
    project.getMessageBus().syncPublisher(Listener.TOPIC).updateFinished();
  }

  private void updatePathPrefixesForAllModules(Project project) {
    if (!project.isDisposed()) {
      Stream.of(ModuleManager.getInstance(project).getModules())
        .forEach(m -> getService(m, ModuleBindingManager.class).updatePathPrefixes(engine));
    }
  }

  private static void analyzeOpenFiles(Project project) {
    if (!project.isDisposed()) {
      var console = SonarLintConsole.get(project);
      console.info("Clearing all issues because binding was updated");

      var store = getService(project, IssueManager.class);
      store.clearAllIssuesForAllFiles();

      var submitter = getService(project, SonarLintSubmitter.class);
      submitter.submitOpenFilesAuto(TriggerType.BINDING_UPDATE);
    }
  }

  public interface Listener {
    Topic<Listener> TOPIC = Topic.create("SonarLint Binding Update Status", Listener.class);

    void updateFinished();
  }

  private static class ProjectStorageUpdateFailure {

    private final String projectKey;
    private final Exception reason;

    public ProjectStorageUpdateFailure(String projectKey, Exception reason) {
      this.projectKey = projectKey;
      this.reason = reason;
    }

    public String getProjectKey() {
      return projectKey;
    }

    public Exception getReason() {
      return reason;
    }

  }
}
