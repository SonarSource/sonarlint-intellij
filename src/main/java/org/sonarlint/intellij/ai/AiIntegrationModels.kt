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

import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationCheckStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationRecordingStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse

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

data class AgentCliIntegration(
    val agent: AiAgent,
    val recordingStatus: CliIntegrationRecordingStatus,
    val configurations: List<CliIntegrationConfig>
)

data class CliIntegrationConfig(
    val path: String?,
    val mcp: CliIntegrationCheckStatus?,
    val hooks: CliIntegrationCheckStatus?
)

data class AiIntegrationSnapshot(
    val cli: CliState,
    val agents: List<AgentCapability>,
    val connectionChoices: List<IntegrationConnection>,
    val recommendedConnectionId: String?,
    val cliIntegrations: List<AgentCliIntegration> = emptyList(),
    val mcpConfigurations: Map<AiAgent, McpAgentConfiguration> = emptyMap()
)

enum class McpConfigurationKind {
    NOT_CONFIGURED,
    STANDALONE,
    CLI_MANAGED,
    CLI_ONLY,
    UNKNOWN,
    MALFORMED
}

data class McpInspection(
    val state: McpConfigurationKind,
    val diagnostics: List<String>
)

data class McpUpdatePlan(
    val state: McpConfigurationKind,
    val updatedContent: String?,
    val diagnostics: List<String>
)

data class McpAgentConfiguration(
    val agent: AiAgent,
    val path: java.nio.file.Path?,
    val state: McpConfigurationKind,
    val diagnostics: List<String>
)

data class CliCommand(
    val executable: String,
    val arguments: List<String>,
    val interactive: Boolean
)

internal fun toAiIntegrationSnapshot(response: GetAiIntegrationStateResponse): AiIntegrationSnapshot {
    val cli = response.cli
    return AiIntegrationSnapshot(
        CliState(cli.installationStatus, cli.authenticationStatus, cli.version, cli.serverUrl, cli.organization),
        response.agents.map { capability ->
            AgentCapability(
                capability.agent,
                capability.detectionSources.toSet(),
                capability.isCliIntegrationSupported,
                capability.isStandaloneMcpSupported
            )
        },
        response.connectionChoices.map { connection ->
            IntegrationConnection(connection.connectionId, connection.serverUrl, connection.organization)
        },
        response.recommendedConnectionId,
        response.cliIntegrations.orEmpty().map { integration ->
            AgentCliIntegration(
                integration.agent,
                integration.recordingStatus ?: CliIntegrationRecordingStatus.UNKNOWN,
                integration.configurations.orEmpty().map { configuration ->
                    CliIntegrationConfig(configuration.path, configuration.mcp, configuration.hooks)
                }
            )
        }
    )
}

sealed interface AiIntegrationsPanelState {
    data object Loading : AiIntegrationsPanelState
    data class Ready(val snapshot: AiIntegrationSnapshot) : AiIntegrationsPanelState
    data class Empty(val snapshot: AiIntegrationSnapshot) : AiIntegrationsPanelState
    data class Error(val message: String) : AiIntegrationsPanelState
}
sealed interface AiIntegrationsIntent {
    data object Refresh : AiIntegrationsIntent
    data object OpenCliDocumentation : AiIntegrationsIntent
    data object InstallCli : AiIntegrationsIntent
    data object AuthenticateCli : AiIntegrationsIntent
    data class IntegrateCli(val agent: AiAgent) : AiIntegrationsIntent
    data class SetUpMcp(val agent: AiAgent) : AiIntegrationsIntent
    data class OpenMcpConfiguration(val agent: AiAgent) : AiIntegrationsIntent
    data object OpenConnectionSettings : AiIntegrationsIntent
}
