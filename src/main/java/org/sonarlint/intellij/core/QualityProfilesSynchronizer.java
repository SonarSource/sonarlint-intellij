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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class QualityProfilesSynchronizer implements Disposable {

  private final Project myProject;
  private ScheduledFuture<?> scheduledTask;

  public QualityProfilesSynchronizer(Project project) {
    myProject = project;
  }

  public void init() {
    var syncPeriod = Long.parseLong(StringUtils.defaultIfBlank(System.getenv("SONARLINT_INTERNAL_SYNC_PERIOD"), "3600"));
    scheduledTask = JobScheduler.getScheduler().scheduleWithFixedDelay(this::syncQualityProfiles, 1, syncPeriod, TimeUnit.SECONDS);
  }

  private void syncQualityProfiles() {
    ProgressManager.getInstance()
      .run(new Task.Backgroundable(myProject, "Checking SonarLint Binding Updates") {
        public void run(@NotNull ProgressIndicator progressIndicator) {
          QualityProfilesSynchronizer.this.syncQualityProfiles(progressIndicator);
        }
      });

  }

  void syncQualityProfiles(@NotNull ProgressIndicator progressIndicator) {
    ProjectBindingManager projectBindingManager;
    var log = getService(GlobalLogOutput.class);
    ConnectedSonarLintEngine engine;
    try {
      projectBindingManager = getService(myProject, ProjectBindingManager.class);
      engine = projectBindingManager.getConnectedEngine();
    } catch (Exception e) {
      // happens if project is not bound, binding is invalid, storages are not updated, ...
      log.log("Couldn't get a connected engine to sync quality profiles: " + e.getMessage(), ClientLogOutput.Level.DEBUG);
      return;
    }

    try {
      var serverConnection = projectBindingManager.getServerConnection();
      progressIndicator.setIndeterminate(false);
      var projectKeysToSync = getService(myProject, ProjectBindingManager.class).getUniqueProjectKeys();
      log.log("Sync quality profiles...", ClientLogOutput.Level.INFO);
      engine.sync(serverConnection.getEndpointParams(), serverConnection.getHttpClient(), projectKeysToSync, new TaskProgressMonitor(progressIndicator, myProject));
    } catch (Exception e) {
      log.log("There was an error while synchronizing quality profiles: " + e.getMessage(), ClientLogOutput.Level.WARN);
    }

    var submitter = getService(myProject, SonarLintSubmitter.class);
    submitter.submitOpenFilesAuto(TriggerType.BINDING_UPDATE);
  }

  @Override
  public void dispose() {
    scheduledTask.cancel(true);
  }
}
