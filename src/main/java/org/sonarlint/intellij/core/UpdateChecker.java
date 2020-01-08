/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;

public class UpdateChecker extends AbstractProjectComponent {

  private final ProjectBindingManager projectBindingManager;
  private final SonarLintProjectSettings projectSettings;
  private final SonarLintProjectNotifications notifications;
  private ScheduledFuture<?> scheduledTask;
  private final GlobalLogOutput log;

  public UpdateChecker(Project project, ProjectBindingManager projectBindingManager, SonarLintProjectSettings projectSettings, SonarLintProjectNotifications notifications) {
    super(project);
    this.projectBindingManager = projectBindingManager;
    this.projectSettings = projectSettings;
    this.notifications = notifications;
    this.log = GlobalLogOutput.get();
  }

  @Override
  public void initComponent() {
    scheduledTask = JobScheduler.getScheduler().scheduleWithFixedDelay(this::checkForUpdate, 10, 24L * 60L * 60L, TimeUnit.SECONDS);
  }

  @Override
  public void projectClosed() {
    scheduledTask.cancel(true);
  }

  private void checkForUpdate() {
    ProgressManager.getInstance()
      .run(new Task.Backgroundable(myProject, "Checking SonarLint Binding Updates") {
        public void run(@NotNull ProgressIndicator progressIndicator) {
          UpdateChecker.this.checkForUpdate(progressIndicator);
        }
      });

  }

  void checkForUpdate(@NotNull ProgressIndicator progressIndicator) {
    ConnectedSonarLintEngine engine;
    try {
      engine = projectBindingManager.getConnectedEngine();
    } catch (Exception e) {
      // happens if project is not bound, binding is invalid, storages are not updated, ...
      log.log("Couldn't get a connected engine to check for update: " + e.getMessage(), LogOutput.Level.DEBUG);
      return;
    }

    try {
      List<String> changelog = new ArrayList<>();
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(projectBindingManager.getSonarQubeServer());
      log.log("Check for updates from server '" + projectBindingManager.getSonarQubeServer().getName() + "'...", LogOutput.Level.INFO);
      boolean hasGlobalUpdates = checkForGlobalUpdates(changelog, engine, serverConfiguration, progressIndicator);
      log.log("Check for updates from server '" + projectBindingManager.getSonarQubeServer().getName() +
        "' for project '" + projectSettings.getProjectKey() + "'...", LogOutput.Level.INFO);
      checkForProjectUpdates(changelog, engine, serverConfiguration, progressIndicator);
      if (!changelog.isEmpty()) {
        changelog.forEach(line -> log.log("  - " + line, LogOutput.Level.INFO));
        notifications.notifyServerHasUpdates(projectSettings.getServerId(), engine, projectBindingManager.getSonarQubeServer(), !hasGlobalUpdates);
      }
    } catch (Exception e) {
      log.log("There was an error while checking for updates: " + e.getMessage(), LogOutput.Level.WARN);
    }
  }

  private void checkForProjectUpdates(List<String> changelog, ConnectedSonarLintEngine engine, ServerConfiguration serverConfiguration, ProgressIndicator indicator) {
    StorageUpdateCheckResult projectUpdateCheckResult = engine.checkIfProjectStorageNeedUpdate(serverConfiguration, projectSettings.getProjectKey(),
      new TaskProgressMonitor(indicator));
    if (projectUpdateCheckResult.needUpdate()) {
      changelog.addAll(projectUpdateCheckResult.changelog());
    }
  }

  private static boolean checkForGlobalUpdates(List<String> changelog, ConnectedSonarLintEngine engine, ServerConfiguration serverConfiguration, ProgressIndicator indicator) {
    StorageUpdateCheckResult checkForUpdateResult = engine.checkIfGlobalStorageNeedUpdate(serverConfiguration, new TaskProgressMonitor(indicator));
    if (checkForUpdateResult.needUpdate()) {
      changelog.addAll(checkForUpdateResult.changelog());
      return true;
    }
    return false;
  }

}
