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
package org.sonarlint.intellij.telemetry

import java.util.concurrent.CompletableFuture
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AddQuickFixAppliedForRuleParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.DevNotificationsClickedParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionResolvedParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.HelpAndFeedbackClickedParams

class SonarLintTelemetryImpl : SonarLintTelemetry {
    override fun optOut(optOut: Boolean) {
        if (optOut) {
            notifyTelemetry { it.disableTelemetry() }
        } else {
            notifyTelemetry { it.enableTelemetry() }
        }
    }

    override fun enabled(): CompletableFuture<Boolean> {
        return getService(BackendService::class.java).isTelemetryEnabled()
    }

    override fun devNotificationsClicked(eventType: String) {
        notifyTelemetry { it.devNotificationsClicked(DevNotificationsClickedParams(eventType)) }
    }

    override fun taintVulnerabilitiesInvestigatedRemotely() {
        notifyTelemetry { it.taintVulnerabilitiesInvestigatedRemotely() }
    }

    override fun taintVulnerabilitiesInvestigatedLocally() {
        notifyTelemetry { it.taintVulnerabilitiesInvestigatedLocally() }
    }

    override fun addQuickFixAppliedForRule(ruleKey: String) {
        notifyTelemetry { it.addQuickFixAppliedForRule(AddQuickFixAppliedForRuleParams(ruleKey)) }
    }

    override fun helpAndFeedbackLinkClicked(itemId: String) {
        notifyTelemetry { it.helpAndFeedbackLinkClicked(HelpAndFeedbackClickedParams(itemId)) }
    }

    override fun addedAutomaticBindings() {
        notifyTelemetry { it.addedAutomaticBindings() }
    }

    override fun addedImportedBindings() {
        notifyTelemetry { it.addedImportedBindings() }
    }

    override fun addedManualBindings() {
        notifyTelemetry { it.addedManualBindings() }
    }

    override fun fixSuggestionResolved(suggestionId: String, status: FixSuggestionStatus, snippetIndex: Int?) {
        notifyTelemetry { it.fixSuggestionResolved(FixSuggestionResolvedParams(suggestionId, status, snippetIndex)) }
    }

    companion object {
        private fun notifyTelemetry(action: (TelemetryRpcService) -> Unit) {
            getService(BackendService::class.java).notifyTelemetry(action)
        }
    }
}
