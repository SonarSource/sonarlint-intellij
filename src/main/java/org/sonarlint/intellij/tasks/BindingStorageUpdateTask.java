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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.Topic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ModuleBindingManager;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

public class BindingStorageUpdateTask {
  private final ConnectedSonarLintEngine engine;
  private final ServerConnection connection;
  private final Map<String, List<Project>> projectsPerProjectKey;
  private final boolean onlyModules;

  public BindingStorageUpdateTask(ConnectedSonarLintEngine engine, ServerConnection connection, Map<String, List<Project>> projectsPerProjectKey, boolean onlyModules) {
    this.engine = engine;
    this.connection = connection;
    this.projectsPerProjectKey = projectsPerProjectKey;
    this.onlyModules = onlyModules;
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
    indicator.setText("Fetching data...");

    try {
      indicator.setIndeterminate(false);
      TaskProgressMonitor monitor = new TaskProgressMonitor(indicator, null);

      EndpointParams connectedModeEndpoint = connection.getEndpointParams();
      if (!onlyModules) {
        UpdateResult updateResult = engine.update(connectedModeEndpoint, connection.getHttpClient(), monitor);
        Collection<SonarAnalyzer> tooOld = updateResult.analyzers().stream()
          .filter(SonarAnalyzer::sonarlintCompatible)
          .filter(BindingStorageUpdateTask::tooOld)
          .collect(Collectors.toList());
        if (!tooOld.isEmpty()) {
          ApplicationManager.getApplication().invokeAndWait(() -> Messages.showWarningDialog(buildMinimumVersionFailMessage(tooOld), "Analyzers Not Loaded"), ModalityState.any());
        }
        GlobalLogOutput.get().log("Server binding '" + connection.getName() + "' updated", LogOutput.Level.INFO);
      }

      updateProjects(connection, monitor);

    } catch (CanceledException e) {
      GlobalLogOutput.get().log("Update of server '" + connection.getName() + "' was cancelled", LogOutput.Level.INFO);
    } catch (Exception e) {
      GlobalLogOutput.get().logError("Error updating from server '" + connection.getName() + "'", e);
      final String msg = (e.getMessage() != null) ? e.getMessage() : ("Failed to update binding for server connection '" + connection.getName() + "'");
      ApplicationManager.getApplication().invokeAndWait(new RunnableAdapter() {
        @Override
        public void doRun() {
          Messages.showErrorDialog((Project) null, msg, "Update Failed");
        }
      }, ModalityState.any());
    }
  }

  private static String buildMinimumVersionFailMessage(Collection<SonarAnalyzer> failingAnalyzers) {
    String msg = "The following plugins do not meet the required minimum versions: ";

    return msg + failingAnalyzers.stream()
      .map(BindingStorageUpdateTask::analyzerToString)
      .collect(Collectors.joining(","));
  }

  private static String analyzerToString(SonarAnalyzer analyzer) {
    return analyzer.key()
      + " (installed: " + analyzer.version()
      + ", minimum: " + analyzer.minimumVersion() + ")";
  }

  private static boolean tooOld(SonarAnalyzer analyzer) {
    if (analyzer.minimumVersion() != null && analyzer.version() != null) {
      Version minimum = Version.create(analyzer.minimumVersion());
      Version version = Version.create(analyzer.version());
      return version.compareTo(minimum) < 0;
    }
    return false;
  }

  /**
   * Updates all known projects belonging to a server configuration.
   * It assumes that the server binding is updated.
   */
  private void updateProjects(ServerConnection connection, TaskProgressMonitor monitor) {
    Set<String> failedProjects = new LinkedHashSet<>();
    for (Map.Entry<String, List<Project>> entry : projectsPerProjectKey.entrySet()) {
      try {
        updateProject(connection, entry.getKey(), entry.getValue(), monitor);
      } catch (Throwable e) {
        // in case of error, save project key and keep updating other projects
        GlobalLogOutput.get().logError(e.getMessage(), e);
        failedProjects.add(entry.getKey());
      }
    }

    if (!projectsPerProjectKey.isEmpty() && !failedProjects.isEmpty()) {
      String errorMsg = "Failed to update the following projects. "
        + "Please check if the server bindings are updated and the module key is correct: "
        + failedProjects.toString();
      GlobalLogOutput.get().log(errorMsg, LogOutput.Level.WARN);

      ApplicationManager.getApplication().invokeLater(new RunnableAdapter() {
        @Override
        public void doRun() {
          Messages.showWarningDialog((Project) null, errorMsg, "Projects Not Updated");
        }
      }, ModalityState.any());
    }
  }

  private void updateProject(ServerConnection connection, String projectKey, List<Project> projects, TaskProgressMonitor monitor) {
    engine.updateProject(connection.getEndpointParams(), connection.getHttpClient(), projectKey, true, monitor);
    GlobalLogOutput.get().log("Project '" + projectKey + "' in server binding '" + this.connection.getName() + "' updated", LogOutput.Level.INFO);
    projects.forEach(this::updateModules);
    projects.forEach(BindingStorageUpdateTask::analyzeOpenFiles);
    projects.forEach(BindingStorageUpdateTask::notifyBindingStorageUpdated);
  }

  private static void notifyBindingStorageUpdated(Project project) {
    project.getMessageBus().syncPublisher(Listener.TOPIC).updateFinished();
  }

  private void updateModules(Project project) {
    if (!project.isDisposed()) {
      Module[] modules = ModuleManager.getInstance(project).getModules();

      for (Module m : modules) {
        SonarLintUtils.getService(m, ModuleBindingManager.class).updateBinding(engine);
      }
    }
  }

  private static void analyzeOpenFiles(Project project) {
    if (!project.isDisposed()) {
      SonarLintConsole console = SonarLintConsole.get(project);
      console.info("Clearing all issues because binding was updated");

      IssueManager store = SonarLintUtils.getService(project, IssueManager.class);
      store.clearAllIssuesForAllFiles();

      SonarLintSubmitter submitter = SonarLintUtils.getService(project, SonarLintSubmitter.class);
      submitter.submitOpenFilesAuto(TriggerType.BINDING_UPDATE);
    }
  }

  public interface Listener {
    Topic<Listener> TOPIC = Topic.create("SonarLint Binding Update Status", Listener.class);

    void updateFinished();
  }
}
