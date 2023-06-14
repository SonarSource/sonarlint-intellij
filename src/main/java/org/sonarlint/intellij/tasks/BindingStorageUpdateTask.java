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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.core.EngineManager;
import org.sonarlint.intellij.core.ModuleBindingManager;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.finding.persistence.FindingsCache;
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesPresenter;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsPresenter;
import org.sonarlint.intellij.messages.ServerBranchesListenerKt;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class BindingStorageUpdateTask {
  private final ServerConnection connection;
  private final Project onlyForProject;
  @Nullable
  private final Runnable onComplete;

  public BindingStorageUpdateTask(ServerConnection connection, @Nullable Project onlyForProject, @Nullable Runnable onComplete) {
    this.connection = connection;
    this.onlyForProject = onlyForProject;
    this.onComplete = onComplete;
  }

  public BindingStorageUpdateTask(ServerConnection connection, @Nullable Project onlyForProject) {
    this(connection, onlyForProject, null);
  }

  public Task.Modal asModal() {
    return new Task.Modal(null, "Updating local storage for connection '" + connection.getName() + "'", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        BindingStorageUpdateTask.this.run(indicator);
      }
    };
  }

  public Task.Backgroundable asBackground() {
    return new Task.Backgroundable(null, "Updating local storage for connection '" + connection.getName() + "'", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        BindingStorageUpdateTask.this.run(indicator);
      }
    };
  }

  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Local storage update...");

    try {
      indicator.setIndeterminate(false);
      var monitor = new TaskProgressMonitor(indicator, null);

      var serverManager = getService(EngineManager.class);
      var engine = serverManager.getConnectedEngine(connection.getName());

      updateProjectStorages(engine, connection, monitor);

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
    } finally {
      if (onComplete != null) {
        onComplete.run();
      }
    }
  }

  /**
   * Updates all known projects belonging to a connection. Except if project class attribute is provided.
   * It assumes that the global storage was updated previously.
   */
  private void updateProjectStorages(ConnectedSonarLintEngine engine, ServerConnection connection, TaskProgressMonitor monitor) {
    var projectsToUpdate = collectProjectsToUpdate(connection);

    var failures = tryUpdateProjectStorages(engine, connection, monitor, projectsToUpdate);

    projectsToUpdate.forEach(p -> updatePathPrefixesForAllModules(engine, p));
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

  private static List<ProjectStorageUpdateFailure> tryUpdateProjectStorages(ConnectedSonarLintEngine engine, ServerConnection connection, TaskProgressMonitor monitor,
    Collection<Project> projectsToUpdate) {
    var failures = new ArrayList<ProjectStorageUpdateFailure>();

    Set<String> allProjectKeysToSync = new HashSet<>();
    for (Project project : projectsToUpdate) {
      ProjectBindingManager bindingManager = getService(project, ProjectBindingManager.class);
      var projectKeysToSync = bindingManager.getUniqueProjectKeys();
      allProjectKeysToSync.addAll(projectKeysToSync);
    }

    var httpClient = getService(BackendService.class).getHttpClient(connection.getName());
    allProjectKeysToSync.forEach(projectKey -> {
      try {
        engine.updateProject(connection.getEndpointParams(), httpClient, projectKey, monitor);
      } catch (Exception e) {
        GlobalLogOutput.get().logError(e.getMessage(), e);
        failures.add(new ProjectStorageUpdateFailure(projectKey, e));
      }
    });
    engine.sync(connection.getEndpointParams(), httpClient, allProjectKeysToSync, monitor);
    projectsToUpdate.forEach(project -> project.getMessageBus().syncPublisher(ServerBranchesListenerKt.getSERVER_BRANCHES_TOPIC()).serverBranchesUpdated());
    updateAllProjectFindingsForCurrentBranch(engine, connection, monitor, projectsToUpdate);
    return failures;
  }

  private static void updateAllProjectFindingsForCurrentBranch(ConnectedSonarLintEngine engine, ServerConnection connection, TaskProgressMonitor monitor,
    Collection<Project> projectsToUpdate) {
    var allProjectAndBranchesToSync = new HashSet<ProjectBindingManager.ProjectKeyAndBranch>();
    for (Project project : projectsToUpdate) {
      ProjectBindingManager bindingManager = getService(project, ProjectBindingManager.class);
      var projectAndBranchesToSync = bindingManager.getUniqueProjectKeysAndBranchesPairs();
      allProjectAndBranchesToSync.addAll(projectAndBranchesToSync);
    }
    var httpClient = getService(BackendService.class).getHttpClient(connection.getName());
    allProjectAndBranchesToSync
      .forEach(pb -> {
        var branchName = pb.getBranchName();
        if (branchName == null) {
          GlobalLogOutput.get().log("Skip synchronizing issues, branch is unknown", ClientLogOutput.Level.DEBUG);
          return;
        }
        engine.downloadAllServerIssues(connection.getEndpointParams(), httpClient, pb.getProjectKey(), branchName, monitor);
        engine.downloadAllServerHotspots(connection.getEndpointParams(), httpClient, pb.getProjectKey(), branchName, monitor);

        if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
          engine.syncServerTaintIssues(connection.getEndpointParams(), httpClient, pb.getProjectKey(), branchName, monitor);
        }
      });

    if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
      projectsToUpdate.forEach(project -> getService(project, TaintVulnerabilitiesPresenter.class).refreshTaintVulnerabilitiesForOpenFiles());
    }
    projectsToUpdate.forEach(project -> getService(project, SecurityHotspotsPresenter.class).presentSecurityHotspotsForOpenFiles());
  }

  private Collection<Project> collectProjectsToUpdate(ServerConnection connection) {
    return onlyForProject != null ? Collections.singleton(onlyForProject)
      : Stream.of(ProjectManager.getInstance().getOpenProjects())
        .filter(p -> getSettingsFor(p).isBoundTo(connection))
        .collect(Collectors.toList());
  }

  private static void notifyBindingStorageUpdated(Project project) {
    project.getMessageBus().syncPublisher(Listener.TOPIC).updateFinished();
  }

  private static void updatePathPrefixesForAllModules(ConnectedSonarLintEngine engine, Project project) {
    if (!project.isDisposed()) {
      Stream.of(ModuleManager.getInstance(project).getModules())
        .forEach(m -> getService(m, ModuleBindingManager.class).updatePathPrefixes(engine));
    }
  }

  private static void analyzeOpenFiles(Project project) {
    if (!project.isDisposed()) {
      var console = SonarLintConsole.get(project);
      console.info("Clearing all findings because binding was updated");

      var store = getService(project, FindingsCache.class);
      store.clearAllFindingsForAllFiles();

      getService(project, AnalysisSubmitter.class).autoAnalyzeOpenFiles(TriggerType.BINDING_UPDATE);
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
