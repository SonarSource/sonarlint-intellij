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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.common.vcs.VcsListener;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.issue.vulnerabilities.TaintVulnerabilitiesPresenter;
import org.sonarlint.intellij.messages.ServerBranchesListenerKt;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ConnectedModeStorageSynchronizer implements Disposable {

  private final Project myProject;
  private ScheduledFuture<?> scheduledTask;

  public ConnectedModeStorageSynchronizer(Project project) {
    myProject = project;
    project.getMessageBus().connect(project).subscribe(VcsListener.TOPIC, this::updateIssues);
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
      engine.sync(serverConnection.getEndpointParams(), serverConnection.getHttpClient(), projectKeysToSync, new TaskProgressMonitor(progressIndicator, myProject));
      myProject.getMessageBus().syncPublisher(ServerBranchesListenerKt.getSERVER_BRANCHES_TOPIC()).serverBranchesUpdated();

      var projectAndBranchesToSync = bindingManager.getUniqueProjectKeysAndBranchesPairs();
      projectAndBranchesToSync.forEach(pb -> syncIssuesForBranch(engine, serverConnection, pb.getProjectKey(), pb.getBranchName(), progressIndicator));
    } catch (Exception e) {
      log.log("There was an error while synchronizing quality profiles: " + e.getMessage(), ClientLogOutput.Level.WARN);
    }

    var submitter = getService(myProject, SonarLintSubmitter.class);
    submitter.submitOpenFilesAuto(TriggerType.BINDING_UPDATE);
  }

  private void updateIssues(Module module, @Nullable String branchName) {
    if (branchName == null) {
      SonarLintConsole.get(module.getProject()).debug("Skip synchronizing issues, branch is unknown");
      return;
    }
    var log = getService(GlobalLogOutput.class);
    ProjectBindingManager projectBindingManager = getService(myProject, ProjectBindingManager.class);
    if (!projectBindingManager.isBindingValid()) {
      log.log("Invalid binding for project", ClientLogOutput.Level.WARN);
      return;
    }
    var moduleProjectKey = getService(module, ModuleBindingManager.class).resolveProjectKey();
    if (moduleProjectKey == null) {
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

    ProgressManager.getInstance()
      .run(new Task.Backgroundable(myProject, "Pull server issues after switching branch") {
        public void run(@NotNull ProgressIndicator progressIndicator) {
          try {
            var serverConnection = projectBindingManager.getServerConnection();
            progressIndicator.setIndeterminate(false);
            syncIssuesForBranch(engine, serverConnection, moduleProjectKey, branchName, progressIndicator);
            if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
              getService(myProject, TaintVulnerabilitiesPresenter.class).presentTaintVulnerabilitiesForOpenFiles();
            }
          } catch (Exception e) {
            log.log("There was an error while synchronizing issues: " + e.getMessage(), ClientLogOutput.Level.WARN);
          }
        }
      });
  }

  private void syncIssuesForBranch(ConnectedSonarLintEngine engine, ServerConnection serverConnection, String projectKey, @Nullable String branchName,
    ProgressIndicator progressIndicator) {
    if (branchName == null) {
      SonarLintConsole.get(myProject).debug("Skip synchronizing issues, branch is unknown");
      return;
    }
    engine.syncServerIssues(serverConnection.getEndpointParams(), serverConnection.getHttpClient(), projectKey, branchName,
      new TaskProgressMonitor(progressIndicator, myProject));
    if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
      engine.syncServerTaintIssues(serverConnection.getEndpointParams(), serverConnection.getHttpClient(), projectKey, branchName,
        new TaskProgressMonitor(progressIndicator, myProject));
    }
  }

  @Override
  public void dispose() {
    scheduledTask.cancel(true);
  }
}
