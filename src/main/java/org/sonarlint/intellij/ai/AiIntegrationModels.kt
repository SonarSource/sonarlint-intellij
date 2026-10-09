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

import com.intellij.openapi.util.SystemInfo
import java.util.concurrent.CompletionException
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationCheckStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationRecordingStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse

data class CliState(
    val installation: CliInstallationStatus,
    val authentication: CliAuthenticationStatus,
    val version: String?,
    val serverUrl: String?,
    val organization: String?,
    val uninstallAvailable: Boolean = false
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
    val cliIntegrations: List<AgentCliIntegration> = emptyList()
)

data class CliCommand(
    val executable: String,
    val arguments: List<String>,
    val interactive: Boolean
)

internal fun toAiIntegrationSnapshot(response: GetAiIntegrationStateResponse): AiIntegrationSnapshot {
    val cli = response.cli
    return AiIntegrationSnapshot(
        CliState(
            cli.installationStatus,
            cli.authenticationStatus,
            cli.version,
            cli.serverUrl,
            cli.organization,
            cli.isUninstallAvailable
        ),
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
    data object InstallCli : AiIntegrationsIntent
    data object AuthenticateCli : AiIntegrationsIntent
    data class IntegrateCli(val agent: AiAgent) : AiIntegrationsIntent
    data object UninstallCli : AiIntegrationsIntent
}

sealed interface CliUninstallFeedback {
    data object InProgress : CliUninstallFeedback
    data class Finished(
        val summary: String,
        val resetOutput: String,
        val cleanupWarnings: String,
        val status: UninstallCliResponse.Status = UninstallCliResponse.Status.FAILED
    ) : CliUninstallFeedback {
        val tokenRevocationWarning: String?
            get() = if (cleanupWarnings.contains("could not be revoked", ignoreCase = true) ||
                cleanupWarnings.contains("failed to revoke the server-side token", ignoreCase = true)) {
                "Some server tokens could not be revoked automatically. Revoke them manually in SonarQube."
            } else null
    }
}

internal fun uninstallFeedback(response: UninstallCliResponse): CliUninstallFeedback.Finished {
    val summary = when (response.status) {
        UninstallCliResponse.Status.UNINSTALLED -> listOfNotNull(
            "SonarQube CLI removed.",
            response.message?.takeIf { it.isNotBlank() },
            cliPathCleanupHint()
        ).joinToString("\n\n")
        UninstallCliResponse.Status.FAILED -> response.message?.takeIf { it.isNotBlank() }
            ?: "SonarQube CLI could not be uninstalled."
        UninstallCliResponse.Status.NOT_AVAILABLE -> response.message?.takeIf { it.isNotBlank() }
            ?: "Only an official per-user CLI installation can be uninstalled."
    }
    return CliUninstallFeedback.Finished(summary, response.stdout, response.stderr, response.status)
}

internal fun cliPathCleanupHint(windows: Boolean = SystemInfo.isWindows): String = if (windows) {
    "Remove %LOCALAPPDATA%\\sonarqube-cli\\bin from your user Path in Environment Variables. Reopen terminals."
} else {
    "Remove \$HOME/.local/share/sonarqube-cli/bin from PATH in your shell profile (~/.bashrc or ~/.zshrc). Reopen terminals."
}

internal fun uninstallFailure(error: Throwable): CliUninstallFeedback.Finished {
    val cause = if (error is CompletionException && error.cause != null) error.cause!! else error
    return CliUninstallFeedback.Finished(
        cause.message?.takeIf { it.isNotBlank() } ?: "SonarQube CLI could not be uninstalled.",
        "",
        ""
    )
}
