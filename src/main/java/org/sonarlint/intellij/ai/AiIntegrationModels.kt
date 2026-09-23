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

enum class AiAgentId {
    CURSOR,
    GITHUB_COPILOT,
    KIRO,
    WINDSURF,
    CLAUDE_CODE,
    CODEX,
    GITHUB_COPILOT_CLI,
    ANTIGRAVITY
}

enum class AgentDetectionSource {
    IDE,
    CLI
}

enum class CliInstallationState {
    NOT_INSTALLED,
    INSTALLED,
    UNUSABLE
}

enum class CliAuthenticationState {
    AUTHENTICATED,
    UNAUTHENTICATED,
    INVALID,
    UNVERIFIED,
    UNAVAILABLE,
    UNKNOWN
}

data class CliState(
    val installation: CliInstallationState,
    val authentication: CliAuthenticationState,
    val version: String?,
    val serverUrl: String?,
    val organization: String?
)

data class AgentCapability(
    val agent: AiAgentId,
    val detectionSources: Set<AgentDetectionSource>,
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

data class CliCommand(
    val executable: String,
    val arguments: List<String>,
    val interactive: Boolean
)

sealed interface AiIntegrationsPanelState {
    data object Loading : AiIntegrationsPanelState
    data class Ready(val snapshot: AiIntegrationSnapshot) : AiIntegrationsPanelState
    data class Empty(val snapshot: AiIntegrationSnapshot) : AiIntegrationsPanelState
    data class Error(val message: String) : AiIntegrationsPanelState
    data object Remote : AiIntegrationsPanelState
}

sealed interface AiIntegrationsIntent {
    data object Refresh : AiIntegrationsIntent
    data object OpenDocumentation : AiIntegrationsIntent
    data object InstallCli : AiIntegrationsIntent
    data object AuthenticateCli : AiIntegrationsIntent
    data class IntegrateCli(val agent: AiAgentId) : AiIntegrationsIntent
}
