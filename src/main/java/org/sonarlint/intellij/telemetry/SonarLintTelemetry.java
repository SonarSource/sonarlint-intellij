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
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

public class SonarLintTelemetry implements ApplicationComponent {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final Logger LOGGER = Logger.getInstance(SonarLintTelemetry.class);

  private final TelemetryEngineProvider engineProvider;
  private final ProjectManager projectManager;
  private TelemetryManager telemetry;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;

  public SonarLintTelemetry(TelemetryEngineProvider engineProvider, ProjectManager projectManager) {
    this.engineProvider = engineProvider;
    this.projectManager = projectManager;
  }

  public void optOut(boolean optOut) {
    if (telemetry != null) {
      if (optOut) {
        if (telemetry.isEnabled()) {
          telemetry.disable();
        }
      } else {
        if (!telemetry.isEnabled()) {
          telemetry.enable();
        }
      }
    }
  }

  public boolean enabled() {
    return telemetry != null && telemetry.isEnabled();
  }

  @Override
  public void initComponent() {
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      // can't log with GlobalLogOutput to the tool window since at this point no project is open yet
      LOGGER.info("Telemetry disabled by system property");
      return;
    }
    telemetry = engineProvider.get();
    try {
      this.scheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
    } catch (Exception e) {
      if(org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils.isInternalDebugEnabled()) {
        String msg = "Failed to schedule telemetry job";
        LOGGER.error(msg, e);
        GlobalLogOutput.get().logError(msg, e);
      }
    }
  }

  @VisibleForTesting
  void upload() {
    if (enabled()) {
      telemetry.usedConnectedMode(isAnyProjectConnected());
      telemetry.uploadLazily();
    }
  }

  public void usedAnalysis() {
    if (enabled()) {
      telemetry.usedAnalysis();
    }
  }

  @VisibleForTesting
  void stop() {
    if (enabled()) {
      telemetry.stop();
    }

    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
  }

  @Override
  public void disposeComponent() {
    stop();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "SonarLintTelemetry";
  }

  private boolean isAnyProjectConnected() {
    Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.stream(openProjects).anyMatch(p -> SonarLintUtils.get(p, SonarLintProjectSettings.class).isBindingEnabled());
  }
}
