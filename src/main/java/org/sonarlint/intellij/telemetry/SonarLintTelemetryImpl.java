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
package org.sonarlint.intellij.telemetry;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

public class SonarLintTelemetryImpl implements ApplicationComponent, SonarLintTelemetry {
  private static final Logger LOGGER = Logger.getInstance(SonarLintTelemetryImpl.class);
  static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";

  private final TelemetryManager telemetry;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;

  public SonarLintTelemetryImpl(TelemetryManagerProvider telemetryManagerProvider) {
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      // can't log with GlobalLogOutput to the tool window since at this point no project is open yet
      LOGGER.info("Telemetry disabled by system property");
      telemetry = null;
    } else {
      telemetry = telemetryManagerProvider.get();
    }
  }

  @Override
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

  @Override
  public boolean enabled() {
    return telemetry != null && telemetry.isEnabled();
  }

  @Override public boolean canBeEnabled() {
    return telemetry != null;
  }

  @Override
  public void initComponent() {
    try {
      this.scheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
    } catch (Exception e) {
      if (org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils.isInternalDebugEnabled()) {
        String msg = "Failed to schedule telemetry job";
        LOGGER.error(msg, e);
        GlobalLogOutput.get().logError(msg, e);
      }
    }
  }

  @VisibleForTesting
  void upload() {
    if (enabled()) {
      telemetry.uploadLazily();
    }
  }

  @Override
  public void analysisDoneOnMultipleFiles() {
    if (enabled()) {
      telemetry.analysisDoneOnMultipleFiles();
    }
  }

  @Override
  public void analysisDoneOnSingleFile(@Nullable String language, int time) {
    if (enabled()) {
      telemetry.analysisDoneOnSingleLanguage(language, time);
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

}
