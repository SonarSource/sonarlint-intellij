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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.messages.ServerBranchesListenerKt;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

@Service(Service.Level.PROJECT)
public final class ConnectedModeStorageSynchronizer implements Disposable {
  private final Project myProject;
  private ScheduledFuture<?> scheduledTask;

  public ConnectedModeStorageSynchronizer(Project project) {
    myProject = project;
  }

  public void init() {
    var syncPeriod = Long.parseLong(StringUtils.defaultIfBlank(System.getenv("SONARLINT_INTERNAL_SYNC_PERIOD"), "3600"));
    scheduledTask = JobScheduler.getScheduler().scheduleWithFixedDelay(this::sync, 1, syncPeriod, TimeUnit.SECONDS);
  }

  private void sync() {
    if (!getSettingsFor(myProject).isBound()) {
      return;
    }
    ProgressManager.getInstance()
      .run(new Task.Backgroundable(myProject, "Checking SonarLint Binding Updates") {
        public void run(@NotNull ProgressIndicator progressIndicator) {
          ConnectedModeStorageSynchronizer.this.sync(progressIndicator);
        }
      });

  }

  void sync(@NotNull ProgressIndicator progressIndicator) {
    var log = getService(GlobalLogOutput.class);
    ProjectBindingManager projectBindingManager = getService(myProject, ProjectBindingManager.class);
    if (!projectBindingManager.isBindingValid()) {
      log.log("Invalid bindind for project", ClientLogOutput.Level.WARN);
      return;
    }
    ConnectedSonarLintEngine engine;
    try {
      engine = projectBindingManager.getConnectedEngine();
    } catch (Exception e) {
      // happens if project is not bound, binding is invalid, storages are not updated, ...
      log.log("Couldn't get a connected engine to sync: " + e.getMessage(), ClientLogOutput.Level.DEBUG);
      return;
    }

    try {
      var serverConnection = projectBindingManager.getServerConnection();
      progressIndicator.setIndeterminate(false);
      ProjectBindingManager bindingManager = getService(myProject, ProjectBindingManager.class);
      var projectKeysToSync = bindingManager.getUniqueProjectKeys();
      engine.sync(serverConnection.getEndpointParams(), getService(BackendService.class).getHttpClient(serverConnection.getName()), projectKeysToSync,
        new TaskProgressMonitor(progressIndicator, myProject));
      myProject.getMessageBus().syncPublisher(ServerBranchesListenerKt.getSERVER_BRANCHES_TOPIC()).serverBranchesUpdated();
    } catch (Exception e) {
      log.log("There was an error while synchronizing quality profiles: " + e.getMessage(), ClientLogOutput.Level.WARN);
    }

    getService(myProject, AnalysisSubmitter.class).autoAnalyzeOpenFiles(TriggerType.BINDING_UPDATE);
  }

  @Override
  public void dispose() {
    scheduledTask.cancel(true);
  }
}
