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
package org.sonarlint.intellij.telemetry;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.telemetry.Telemetry;

public class SonarLintTelemetry implements ApplicationComponent {
  private static final String PRODUCT = "SonarLint IntelliJ";
  private static final String STORAGE_FILENAME = "sonarlint_usage";

  private final SonarApplication application;
  private final ProjectManager projectManager;

  @VisibleForTesting
  Telemetry telemetry;
  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;

  public SonarLintTelemetry(SonarApplication application, ProjectManager projectManager) {
    this.application = application;
    this.projectManager = projectManager;
    this.telemetry = null;
  }

  public void setEnabled(boolean enabled) {
    if (telemetry != null) {
      telemetry.enable(enabled);
    }
  }

  @Override public void initComponent() {
    try {
      this.telemetry = new Telemetry(getStorageFilePath());
      this.scheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
    } catch (Exception e) {
      // fail silently
    }
  }

  private void upload() {
    if (telemetry != null) {
      TelemetryClientConfig clientConfig = SonarLintUtils.getTelemetryClientConfig();
      telemetry.getClient(PRODUCT, application.getVersion()).tryUpload(clientConfig, isAnyProjectConnected());
    }
  }

  public void analysisSubmitted() {
    if (telemetry != null) {
      telemetry.getDataCollection().analysisDone();
    }
  }

  @Override public void disposeComponent() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
    try {
      if (telemetry != null) {
        telemetry.save();
      }
    } catch (IOException e) {
      // ignore
    }
  }

  @NotNull @Override public String getComponentName() {
    return "SonarLintTelemetry";
  }

  private static Path getStorageFilePath() {
    return Paths.get(PathManager.getSystemPath()).resolve(STORAGE_FILENAME);
  }

  private boolean isAnyProjectConnected() {
    Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.stream(openProjects).anyMatch(p -> SonarLintUtils.get(p, SonarLintProjectSettings.class).isBindingEnabled());
  }
}
