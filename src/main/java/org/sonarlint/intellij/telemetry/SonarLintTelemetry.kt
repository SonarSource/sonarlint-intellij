/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.telemetry

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CompletableFuture
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.monitoring.MonitoringService
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationCliStateObservedParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiAgentIntegrationStateObservedParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingTriggeredParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams

@Service(Service.Level.APP)
class SonarLintTelemetry {
    private val aiDispatchLock = Any()
    private var aiDispatchTail = CompletableFuture.completedFuture<Void>(null)

    fun optOut(optOut: Boolean) {
        if (optOut) {
            notifyTelemetry { it.disableTelemetry() }
            MonitoringService.close()
        } else {
            notifyTelemetry { it.enableTelemetry() }
            MonitoringService.reinit()
        }
    }

    fun enabled(): CompletableFuture<Boolean> {
        return getService(BackendService::class.java).isTelemetryEnabled()
    }

    fun devNotificationsClicked(eventType: String) {
        notifyTelemetry { it.devNotificationsClicked(DevNotificationsClickedParams(eventType)) }
    }

    fun taintVulnerabilitiesInvestigatedRemotely() {
        notifyTelemetry { it.taintVulnerabilitiesInvestigatedRemotely() }
    }

    fun taintVulnerabilitiesInvestigatedLocally() {
        notifyTelemetry { it.taintVulnerabilitiesInvestigatedLocally() }
    }

    fun addQuickFixAppliedForRule(ruleKey: String) {
        notifyTelemetry { it.addQuickFixAppliedForRule(AddQuickFixAppliedForRuleParams(ruleKey)) }
    }

    fun helpAndFeedbackLinkClicked(itemId: String) {
        notifyTelemetry { it.helpAndFeedbackLinkClicked(HelpAndFeedbackClickedParams(itemId)) }
    }

    fun analysisReportingTriggered(analysisType: AnalysisReportingType) {
        notifyTelemetry { it.analysisReportingTriggered(AnalysisReportingTriggeredParams(analysisType)) }
    }

    fun fixSuggestionResolved(suggestionId: String, status: FixSuggestionStatus, snippetIndex: Int?) {
        notifyTelemetry { it.fixSuggestionResolved(FixSuggestionResolvedParams(suggestionId, status, snippetIndex)) }
    }

    fun supportedLanguagesPanelOpened() {
        notifyTelemetry { it.supportedLanguagesPanelOpened() }
    }

    fun supportedLanguagesPanelCtaClicked() {
        notifyTelemetry { it.supportedLanguagesPanelCtaClicked() }
    }

    fun aiIntegrationAction(action: AiIntegrationAction, status: AiIntegrationActionStatus, agent: AiAgent? = null) {
        notifyAiTelemetry { it.aiIntegrationAction(AiIntegrationActionParams(action, status, agent, AiIntegrationHost.INTELLIJ)) }
    }

    fun aiIntegrationCliStateObserved(installation: CliInstallationStatus, authentication: CliAuthenticationStatus) {
        notifyAiTelemetry { it.aiIntegrationCliStateObserved(AiIntegrationCliStateObservedParams(installation, authentication, AiIntegrationHost.INTELLIJ)) }
    }

    fun aiAgentIntegrationStateObserved(agent: AiAgent, sources: List<AiAgentDetectionSource>, state: McpConfigurationState) {
        notifyAiTelemetry { it.aiAgentIntegrationStateObserved(AiAgentIntegrationStateObservedParams(agent, sources, state, AiIntegrationHost.INTELLIJ)) }
    }

    private fun notifyAiTelemetry(action: (TelemetryRpcService) -> Unit) {
        synchronized(aiDispatchLock) {
            try {
                aiDispatchTail = aiDispatchTail.handle<Void> { _, _ -> null }
                    .thenComposeAsync { getService(BackendService::class.java).notifyAiTelemetry(action) }
                    .exceptionally {
                        Logger.getInstance(SonarLintTelemetry::class.java).debug("Unable to dispatch AI integrations telemetry")
                        null
                    }
            } catch (_: Exception) {
                Logger.getInstance(SonarLintTelemetry::class.java).debug("Unable to schedule AI integrations telemetry")
            }
        }
    }

    companion object {
        private fun notifyTelemetry(action: (TelemetryRpcService) -> Unit) {
            runOnPooledThread { getService(BackendService::class.java).notifyTelemetry(action) }
        }
    }

}
