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
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.telemetry.Telemetry;

public class SonarLintTelemetry implements ApplicationComponent {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final Logger LOGGER = Logger.getInstance(SonarLintTelemetry.class);
  private final TelemetryEngineProvider engineProvider;
  private final ProjectManager projectManager;
  private boolean enabled;
  private Telemetry telemetryEngine;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;

  public SonarLintTelemetry(TelemetryEngineProvider engineProvider, ProjectManager projectManager) {
    this.engineProvider = engineProvider;
    this.projectManager = projectManager;
    this.telemetryEngine = null;
  }

  public void optOut(boolean optOut) {
    if (telemetryEngine != null) {
      if (optOut == !telemetryEngine.enabled()) {
        return;
      }
      telemetryEngine.enable(!optOut);
      if (optOut) {
        try {
          TelemetryClientConfig clientConfig = SonarLintUtils.getTelemetryClientConfig();
          telemetryEngine.getClient().optOut(clientConfig, isAnyProjectConnected());
        } catch (Exception e) {
          // fail silently
        }
      }
    }
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean optedIn() {
    return enabled && this.telemetryEngine.enabled();
  }

  @Override public void initComponent() {
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      this.enabled = false;
      // can't log with GlobalLogOutput to the tool window since at this point no project is open yet
      LOGGER.info("Telemetry disabled by system property");
      return;
    }
    try {
      this.telemetryEngine = engineProvider.get();
      this.scheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
      this.enabled = true;
    } catch (Exception e) {
      // fail silently
      enabled = false;
    }
  }

  private void upload() {
    if (enabled) {
      TelemetryClientConfig clientConfig = SonarLintUtils.getTelemetryClientConfig();
      telemetryEngine.getClient().tryUpload(clientConfig, isAnyProjectConnected());
    }
  }

  public void analysisSubmitted() {
    if (enabled) {
      telemetryEngine.getDataCollection().analysisDone();
    }
  }

  @Override public void disposeComponent() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
    try {
      if (telemetryEngine != null) {
        telemetryEngine.save();
      }
    } catch (IOException e) {
      // ignore
    }
  }

  @NotNull @Override public String getComponentName() {
    return "SonarLintTelemetry";
  }

  private boolean isAnyProjectConnected() {
    Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.stream(openProjects).anyMatch(p -> SonarLintUtils.get(p, SonarLintProjectSettings.class).isBindingEnabled());
  }
}
