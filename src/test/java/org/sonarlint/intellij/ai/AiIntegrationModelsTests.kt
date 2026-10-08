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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationAgentCapability
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationConnection
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationCheckStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationConfiguration
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationRecordingStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationState
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetAiIntegrationStateResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.SonarQubeCliState
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse

class AiIntegrationModelsTests {
    @Test
    fun `maps CLI integrations without losing duplicate or pathless configurations`() {
        val configurations = listOf(
            CliIntegrationConfiguration("/config.json", CliIntegrationCheckStatus.CONFIGURED, CliIntegrationCheckStatus.INVALID),
            CliIntegrationConfiguration("/config.json", CliIntegrationCheckStatus.NOT_CONFIGURED, CliIntegrationCheckStatus.UNKNOWN),
            CliIntegrationConfiguration(null, null, CliIntegrationCheckStatus.CONFIGURED),
            CliIntegrationConfiguration(null, null, null)
        )
        val response = GetAiIntegrationStateResponse(
            cli(),
            listOf(AiIntegrationAgentCapability(AiAgent.CLAUDE_CODE, listOf(AiAgentDetectionSource.CLI), true, false)),
            listOf(AiIntegrationConnection("connection", "https://sonar.example", "organization")),
            "connection",
            listOf(
                CliIntegrationState(AiAgent.CLAUDE_CODE, CliIntegrationRecordingStatus.RECORDED, configurations),
                CliIntegrationState(AiAgent.CODEX, CliIntegrationRecordingStatus.NOT_RECORDED, emptyList()),
                CliIntegrationState(AiAgent.KIRO, CliIntegrationRecordingStatus.UNKNOWN, emptyList())
            )
        )

        val snapshot = toAiIntegrationSnapshot(response)

        assertThat(snapshot.cli.authentication).isEqualTo(CliAuthenticationStatus.UNAUTHENTICATED)
        assertThat(snapshot.cli.uninstallAvailable).isFalse()
        assertThat(snapshot.agents.single().agent).isEqualTo(AiAgent.CLAUDE_CODE)
        assertThat(snapshot.connectionChoices).containsExactly(IntegrationConnection("connection", "https://sonar.example", "organization"))
        assertThat(snapshot.recommendedConnectionId).isEqualTo("connection")
        assertThat(snapshot.cliIntegrations).containsExactly(
            AgentCliIntegration(AiAgent.CLAUDE_CODE, CliIntegrationRecordingStatus.RECORDED, listOf(
                CliIntegrationConfig("/config.json", CliIntegrationCheckStatus.CONFIGURED, CliIntegrationCheckStatus.INVALID),
                CliIntegrationConfig("/config.json", CliIntegrationCheckStatus.NOT_CONFIGURED, CliIntegrationCheckStatus.UNKNOWN),
                CliIntegrationConfig(null, null, CliIntegrationCheckStatus.CONFIGURED),
                CliIntegrationConfig(null, null, null)
            )),
            AgentCliIntegration(AiAgent.CODEX, CliIntegrationRecordingStatus.NOT_RECORDED, emptyList()),
            AgentCliIntegration(AiAgent.KIRO, CliIntegrationRecordingStatus.UNKNOWN, emptyList())
        )
    }

    @Test
    fun `accepts an integration response without the optional CLI integration collection`() {
        val response = mock(GetAiIntegrationStateResponse::class.java)
        `when`(response.cli).thenReturn(cli())
        `when`(response.agents).thenReturn(emptyList())
        `when`(response.connectionChoices).thenReturn(emptyList())

        assertThat(toAiIntegrationSnapshot(response).cliIntegrations).isEmpty()
    }

    @Test
    fun `maps missing recording status to unknown and missing configurations to an empty list`() {
        val integration = mock(CliIntegrationState::class.java)
        `when`(integration.agent).thenReturn(AiAgent.CODEX)
        val response = GetAiIntegrationStateResponse(cli(), emptyList(), emptyList(), null, listOf(integration))

        assertThat(toAiIntegrationSnapshot(response).cliIntegrations).containsExactly(
            AgentCliIntegration(AiAgent.CODEX, CliIntegrationRecordingStatus.UNKNOWN, emptyList())
        )
    }

    @Test
    fun `maps uninstall availability and preserves output without inferring cleanup success`() {
        val response = GetAiIntegrationStateResponse(
            SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, null, "1.2.3", null, null, true),
            emptyList(),
            emptyList(),
            null
        )

        assertThat(toAiIntegrationSnapshot(response).cli.uninstallAvailable).isTrue()
        assertThat(uninstallFeedback(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null)).summary)
            .contains("installation was removed", "remaining cleanup", "Reopen terminals")

        val warnings = uninstallFeedback(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "Reset complete", "cleanup warning", "Manual cleanup required"))
        assertThat(warnings.summary).contains("installation was removed", "remaining cleanup", "Manual cleanup required")
            .doesNotContain("reported warnings")
        assertThat(warnings.resetOutput).isEqualTo("Reset complete")
        assertThat(warnings.cleanupWarnings).isEqualTo("cleanup warning")

        assertThat(uninstallFeedback(UninstallCliResponse(UninstallCliResponse.Status.FAILED, "", "reset failed", "SonarQube CLI reset failed.")).summary)
            .isEqualTo("SonarQube CLI reset failed.")
        assertThat(uninstallFeedback(UninstallCliResponse(
            UninstallCliResponse.Status.FAILED, "", "", "Could not delete the SonarQube CLI installation folder."
        )).summary).isEqualTo("Could not delete the SonarQube CLI installation folder.")
        assertThat(uninstallFeedback(UninstallCliResponse(
            UninstallCliResponse.Status.NOT_AVAILABLE, "", "", "Only an official per-user CLI installation can be uninstalled."
        )).summary).contains("official per-user")
    }

    @Test
    fun `provides manual PATH cleanup instructions for both installers`() {
        assertThat(cliPathCleanupHint(windows = true)).contains("user Path", "%LOCALAPPDATA%\\sonarqube-cli\\bin", "Reopen terminals")
        assertThat(cliPathCleanupHint(windows = false)).contains(
            "export PATH=\"\$HOME/.local/share/sonarqube-cli/bin:\$PATH\"", "~/.bashrc", "~/.zshrc", "Reopen terminals"
        )
    }

    private fun cli() = SonarQubeCliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNAUTHENTICATED, null, "1.2.3", null, null)
}
