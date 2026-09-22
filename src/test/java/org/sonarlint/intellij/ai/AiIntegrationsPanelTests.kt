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

import java.awt.Container
import java.awt.event.ComponentEvent
import javax.swing.JButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests

class AiIntegrationsPanelTests : AbstractSonarLintLightTests() {
    @Test
    fun `renders every top level state and changes layout at the logical threshold`() {
        val panel = AiIntegrationsPanel()
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationState.INSTALLED, CliAuthenticationState.AUTHENTICATED, "1.0", null, null),
            emptyList(),
            emptyList(),
            null
        )
        val states = listOf(
            AiIntegrationsPanelState.Loading,
            AiIntegrationsPanelState.Ready(snapshot),
            AiIntegrationsPanelState.Error("failed"),
            AiIntegrationsPanelState.Empty(snapshot),
            AiIntegrationsPanelState.Remote
        )

        states.forEach { state ->
            panel.render(state)
            assertThat(panel.renderedState()).isEqualTo(state)
            assertThat(panel.components).hasSize(1)
        }

        panel.setSize(AiIntegrationsPanel.WIDE_LAYOUT_THRESHOLD + 200, 600)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        assertThat(panel.isWideLayout()).isTrue()
        assertThat(panel.integrationCardsAreSideBySide()).isTrue()
        assertThat(panel.integrationCardsUseNaturalHeight()).isTrue()
        panel.setSize(500, 600)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        assertThat(panel.isWideLayout()).isFalse()
        assertThat(panel.integrationCardsAreSideBySide()).isFalse()
    }

    @Test
    fun `renders refresh as a button with an icon`() {
        val panel = AiIntegrationsPanel()

        val refreshButton = descendants(panel).filterIsInstance<JButton>().first { it.text == "Refresh" }

        assertThat(refreshButton.icon).isNotNull()
        assertThat(refreshButton.preferredSize.width).isGreaterThan(refreshButton.icon.iconWidth)
    }

    @Test
    fun `keeps the agent inventory collapsed until details are requested`() {
        val panel = AiIntegrationsPanel()
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationState.INSTALLED, CliAuthenticationState.AUTHENTICATED, "1.0", null, null),
            listOf(
                AgentCapability(AiAgentId.CURSOR, setOf(AgentDetectionSource.IDE), true, true),
                AgentCapability(AiAgentId.GITHUB_COPILOT, setOf(AgentDetectionSource.IDE), false, false)
            ),
            emptyList(),
            null
        )

        panel.render(AiIntegrationsPanelState.Ready(snapshot))

        assertThat(labelTexts(panel)).doesNotContain("Not available")
        descendants(panel).filterIsInstance<JButton>().first { it.text == "Configure agents…" }.doClick()
        assertThat(labelTexts(panel)).contains("Available", "Not available")
        assertThat(descendants(panel).filterIsInstance<JButton>().map { it.text }).contains("Hide agent details")
    }

    @Test
    fun `reveals compact MCP actions while preserving ownership and protected states`() {
        val panel = AiIntegrationsPanel()
        val path = java.nio.file.Path.of("/tmp/mcp.json")
        val configurations = listOf(
            McpAgentConfiguration(AiAgentId.CURSOR, path, McpConfigurationKind.NOT_CONFIGURED, false, emptyList()),
            McpAgentConfiguration(AiAgentId.GITHUB_COPILOT, path, McpConfigurationKind.STANDALONE, true, emptyList()),
            McpAgentConfiguration(AiAgentId.CLAUDE_CODE, path, McpConfigurationKind.STANDALONE, false, emptyList()),
            McpAgentConfiguration(AiAgentId.KIRO, path, McpConfigurationKind.CLI_MANAGED, false, emptyList()),
            McpAgentConfiguration(AiAgentId.GITHUB_COPILOT_CLI, null, McpConfigurationKind.CLI_ONLY, false, emptyList()),
            McpAgentConfiguration(AiAgentId.CODEX, path, McpConfigurationKind.UNKNOWN, false, listOf("inspect manually")),
            McpAgentConfiguration(AiAgentId.WINDSURF, path, McpConfigurationKind.MALFORMED, false, listOf("invalid json"))
        ).associateBy { it.agent }
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationState.INSTALLED, CliAuthenticationState.AUTHENTICATED, null, null, null),
            listOf(AgentCapability(AiAgentId.GITHUB_COPILOT_CLI, setOf(AgentDetectionSource.CLI), true, false)),
            emptyList(),
            null,
            configurations
        )

        var lastIntent: AiIntegrationsIntent? = null
        panel.setIntentListener { lastIntent = it }
        panel.render(AiIntegrationsPanelState.Ready(snapshot))

        assertThat(labelTexts(panel)).doesNotContain("Not configured", "Malformed configuration")
        descendants(panel).filterIsInstance<JButton>().first { it.text == "Set up an agent…" }.doClick()

        val buttonLabels = descendants(panel).filterIsInstance<JButton>().map { it.text }
        val text = descendants(panel).filter { it is JBLabel || it is JBTextArea }.joinToString(" ") {
            when (it) {
                is JBLabel -> it.text
                is JBTextArea -> it.text
                else -> ""
            }
        }
        assertThat(buttonLabels).contains("Set up", "Replace…", "Open", "Integrate with CLI")
            .doesNotContain("CLI", "Use CLI")
        assertThat(text).contains("Configured · Connection not verified")
            .contains("Configured externally · Connection not verified")
            .contains("Managed by SonarQube CLI")
            .contains("Configure with SonarQube CLI")
            .contains("Configuration state unknown")
            .contains("Malformed configuration")
            .contains("inspect manually")
            .contains("invalid json")

        descendants(panel).filterIsInstance<JButton>().first { it.text == "Integrate with CLI" }.doClick()
        assertThat(lastIntent).isEqualTo(AiIntegrationsIntent.IntegrateCli(AiAgentId.GITHUB_COPILOT_CLI))
    }

    private fun labelTexts(container: Container) = descendants(container).filterIsInstance<JBLabel>().map { it.text }

    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { component ->
        listOf(component) + if (component is Container) descendants(component) else emptyList()
    }
}
