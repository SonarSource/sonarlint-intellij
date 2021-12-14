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
package org.sonarlint.intellij.telemetry;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.serviceContainer.NonInjectable;

import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

public class SonarLintTelemetryImpl implements SonarLintTelemetry, AppLifecycleListener {
  private static final Logger LOGGER = Logger.getInstance(SonarLintTelemetryImpl.class);
  static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";

  private final TelemetryManager telemetry;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;

  public SonarLintTelemetryImpl() {
    this(new TelemetryManagerProvider());
  }

  @NonInjectable
  SonarLintTelemetryImpl(TelemetryManagerProvider telemetryManagerProvider) {
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      // can't log with GlobalLogOutput to the tool window since at this point no project is open yet
      LOGGER.info("Telemetry disabled by system property");
      telemetry = null;
    } else {
      telemetry = telemetryManagerProvider.get();
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppLifecycleListener.TOPIC, this);
      init();
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


  public void init() {
    try {
      this.scheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
    } catch (Exception e) {
      if (InternalDebug.isEnabled()) {
        var msg = "Failed to schedule telemetry job";
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
  public void analysisDoneOnSingleLanguage(@Nullable Language language, int time) {
    if (enabled()) {
      telemetry.analysisDoneOnSingleLanguage(language, time);
    }
  }

  @Override
  public void devNotificationsReceived(String eventType) {
    if (enabled()) {
      telemetry.devNotificationsReceived(eventType);
    }
  }

  @Override
  public void devNotificationsClicked(String eventType) {
    if (enabled()) {
      telemetry.devNotificationsClicked(eventType);
    }
  }

  @Override
  public void showHotspotRequestReceived() {
    if (enabled()) {
      telemetry.showHotspotRequestReceived();
    }
  }

  @Override
  public void taintVulnerabilitiesInvestigatedRemotely() {
    if (enabled()) {
      telemetry.taintVulnerabilitiesInvestigatedRemotely();
    }
  }

  @Override
  public void taintVulnerabilitiesInvestigatedLocally() {
    if (enabled()) {
      telemetry.taintVulnerabilitiesInvestigatedLocally();
    }
  }

  @Override
  public void addReportedRules(Set<String> ruleKeys) {
    if (enabled()) {
      telemetry.addReportedRules(ruleKeys);
    }
  }

  @Override
  public void addQuickFixAppliedForRule(String ruleKey) {
    if (enabled()) {
      telemetry.addQuickFixAppliedForRule(ruleKey);
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
  public void appWillBeClosed(boolean isRestart) {
    stop();
  }
}
