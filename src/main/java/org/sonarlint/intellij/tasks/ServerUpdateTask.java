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
package org.sonarlint.intellij.tasks;

import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;
import org.sonarsource.sonarlint.core.plugin.Version;

public class ServerUpdateTask {
  private static final Logger LOGGER = Logger.getInstance(ServerUpdateTask.class);
  private final ConnectedSonarLintEngine engine;
  private final SonarQubeServer server;
  private final Map<String, List<Project>> projectsPerModule;
  private final boolean onlyModules;
  private final GlobalLogOutput log;

  public ServerUpdateTask(ConnectedSonarLintEngine engine, SonarQubeServer server, Map<String, List<Project>> projectsPerModule, boolean onlyModules) {
    this.engine = engine;
    this.server = server;
    this.projectsPerModule = projectsPerModule;
    this.onlyModules = onlyModules;
    this.log = GlobalLogOutput.get();
  }

  public Task.Modal asModal() {
    return new Task.Modal(null, "Updating SonarQube server '" + server.getName() + "'", true) {
      @Override public void run(@NotNull ProgressIndicator indicator) {
        ServerUpdateTask.this.run(indicator);
      }
    };
  }

  public Task.Backgroundable asBackground() {
    return new Task.Backgroundable(null, "Updating SonarQube server '" + server.getName() + "'", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      @Override public void run(@NotNull ProgressIndicator indicator) {
        ServerUpdateTask.this.run(indicator);
      }
    };
  }

  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Fetching data...");

    try {
      TaskProgressMonitor monitor = new TaskProgressMonitor(indicator);
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);

      if (!onlyModules) {
        UpdateResult updateResult = engine.update(serverConfiguration, monitor);
        Collection<SonarAnalyzer> tooOld = updateResult.analyzers().stream()
          .filter(SonarAnalyzer::sonarlintCompatible)
          .filter(ServerUpdateTask::tooOld)
          .collect(Collectors.toList());
        if (!tooOld.isEmpty()) {
          ApplicationManager.getApplication().invokeAndWait(() ->
            Messages.showWarningDialog(buildMinimumVersionFailMessage(tooOld), "Analyzers Not Loaded"), ModalityState.any());
        }
        log.log("Server binding '" + server.getName() + "' updated", LogOutput.Level.INFO);
      }

      updateModules(serverConfiguration, monitor);

    } catch (CanceledException e) {
      LOGGER.info("Update of server '" + server.getName() + "' was cancelled");
      log.log("Update of server '" + server.getName() + "' was cancelled", LogOutput.Level.INFO);
    } catch (Exception e) {
      LOGGER.info("Error updating from server '" + server.getName() + "'", e);
      final String msg = (e.getMessage() != null) ? e.getMessage() : ("Failed to update binding for server configuration '" + server.getName() + "'");
      ApplicationManager.getApplication().invokeAndWait(new RunnableAdapter() {
        @Override public void doRun() throws Exception {
          Messages.showErrorDialog((Project) null, msg, "Update Failed");
        }
      }, ModalityState.any());
    }
  }

  private static String buildMinimumVersionFailMessage(Collection<SonarAnalyzer> failingAnalyzers) {
    String msg = "The following plugins do not meet the required minimum versions, please upgrade them: ";

    return msg + failingAnalyzers.stream()
      .map(ServerUpdateTask::analyzerToString)
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

  private void updateModules(ServerConfiguration serverConfiguration, TaskProgressMonitor monitor) {
    //here we assume that server is updated, or this won't work
    final Set<String> existingProjectKeys = engine.allModulesByKey().keySet();
    final Set<String> invalidModules = new HashSet<>();

    for (Map.Entry<String, List<Project>> entry : projectsPerModule.entrySet()) {
      String moduleKey = entry.getKey();
      if (existingProjectKeys.contains(moduleKey)) {
        updateModule(serverConfiguration, moduleKey, entry.getValue(), monitor);
      } else {
        invalidModules.add(moduleKey);
      }
    }

    if (!projectsPerModule.isEmpty() && !invalidModules.isEmpty()) {
      log.log("The following modules could not be updated because they don't exist in the SonarQube server: " + invalidModules.toString(), LogOutput.Level.WARN);

      ApplicationManager.getApplication().invokeLater(new RunnableAdapter() {
        @Override public void doRun() throws Exception {
          Messages.showWarningDialog((Project) null,
            "The following modules could not be updated because they don't exist in the SonarQube server: " + invalidModules.toString(), "Modules Not Updated");
        }
      }, ModalityState.any());
    }
  }

  private void updateModule(ServerConfiguration serverConfiguration, String moduleKey, List<Project> projects, TaskProgressMonitor monitor) {
    try {
      engine.updateModule(serverConfiguration, moduleKey, monitor);
      log.log("Module '" + moduleKey + "' in server binding '" + server.getName() + "' updated", LogOutput.Level.INFO);
      projects.forEach(ServerUpdateTask::analyzeOpenFiles);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      // in case of error, show a message box and keep updating other modules
      final String msg = (e.getMessage() != null) ? e.getMessage() : ("Failed to update binding for server configuration '" + server.getName() + "'");
      ApplicationManager.getApplication().invokeLater(new RunnableAdapter() {
        @Override public void doRun() throws Exception {
          Messages.showErrorDialog((Project) null, msg, "Update Failed");
        }
      }, ModalityState.any());
    }
  }

  private static void analyzeOpenFiles(Project project) {
    if (!project.isDisposed()) {
      SonarLintConsole console = SonarLintConsole.get(project);
      console.info("Clearing all issues because binding was updated");

      IssueManager store = SonarLintUtils.get(project, IssueManager.class);
      store.clear();

      SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
      submitter.submitOpenFilesAuto(TriggerType.BINDING_UPDATE);
    }
  }
}
