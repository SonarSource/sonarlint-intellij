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
package org.sonarlint.intellij.ai

import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent // pragma: allowlist secret
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource // pragma: allowlist secret
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus // pragma: allowlist secret
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus // pragma: allowlist secret

data class CliState(
    val installation: CliInstallationStatus,
    val authentication: CliAuthenticationStatus,
    val version: String?,
    val serverUrl: String?,
    val organization: String?
)

data class AgentCapability(
    val agent: AiAgent,
    val detectionSources: Set<AiAgentDetectionSource>,
    val cliIntegrationSupported: Boolean,
    val standaloneMcpSupported: Boolean
)

data class IntegrationConnection(
    val connectionId: String,
    val serverUrl: String,
    val organization: String?
)

data class AiIntegrationSnapshot(
    val cli: CliState,
    val agents: List<AgentCapability>,
    val connectionChoices: List<IntegrationConnection>,
    val recommendedConnectionId: String?
)

sealed interface AiIntegrationsPanelState {
    data object Loading : AiIntegrationsPanelState
    data class Ready(val snapshot: AiIntegrationSnapshot) : AiIntegrationsPanelState
    data class Empty(val snapshot: AiIntegrationSnapshot) : AiIntegrationsPanelState
    data class Error(val message: String) : AiIntegrationsPanelState
}

sealed interface AiIntegrationsIntent {
    data object Refresh : AiIntegrationsIntent
    data object OpenDocumentation : AiIntegrationsIntent
}
