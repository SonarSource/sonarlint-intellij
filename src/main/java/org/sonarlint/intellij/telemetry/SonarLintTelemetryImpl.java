/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddReportedRulesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisDoneOnSingleLanguageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.computeOnPooledThread;

public class SonarLintTelemetryImpl implements SonarLintTelemetry {

  @Override
  public void optOut(boolean optOut) {
    if (optOut) {
      getTelemetryService().disableTelemetry();
    } else {
      getTelemetryService().enableTelemetry();
    }
  }

  @Override
  public boolean enabled() {
    try {
      return getTelemetryService().getStatus().get().isEnabled();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      GlobalLogOutput.get().logError("Cannot retrieve telemetry status", e);
    }
    return false;
  }

  @Override
  public void analysisDoneOnMultipleFiles() {
    getTelemetryService().analysisDoneOnMultipleFiles();
  }

  @Override
  public void analysisDoneOnSingleLanguage(@Nullable Language language, int time) {
    getTelemetryService().analysisDoneOnSingleLanguage(new AnalysisDoneOnSingleLanguageParams(language, time));
  }

  @Override
  public void devNotificationsClicked(String eventType) {
    getTelemetryService().devNotificationsClicked(new DevNotificationsClickedParams(eventType));
  }

  @Override
  public void taintVulnerabilitiesInvestigatedRemotely() {
    getTelemetryService().taintVulnerabilitiesInvestigatedRemotely();
  }

  @Override
  public void taintVulnerabilitiesInvestigatedLocally() {
    getTelemetryService().taintVulnerabilitiesInvestigatedLocally();
  }

  @Override
  public void addReportedRules(Set<String> ruleKeys) {
    getTelemetryService().addReportedRules(new AddReportedRulesParams(ruleKeys));
  }

  @Override
  public void addQuickFixAppliedForRule(String ruleKey) {
    getTelemetryService().addQuickFixAppliedForRule(new AddQuickFixAppliedForRuleParams(ruleKey));
  }

  @Override
  public void helpAndFeedbackLinkClicked(String itemId) {
    getTelemetryService().helpAndFeedbackLinkClicked(new HelpAndFeedbackClickedParams(itemId));
  }

  @Nullable
  private static TelemetryRpcService getTelemetryService() {
    var service = computeOnPooledThread("Telemetry Service Task", () -> getService(BackendService.class).getTelemetryService());
    if (service == null) {
      GlobalLogOutput.get().log("Cannot retrieve telemetry service", ClientLogOutput.Level.ERROR);
    }
    return service;
  }
}
