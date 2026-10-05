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

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import java.awt.Container
import java.awt.GridLayout
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationCheckStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationRecordingStatus

class AiIntegrationsPanelTests : AbstractSonarLintLightTests() {
    @Test
    fun `renders the current integration statuses`() {
        val panel = AiIntegrationsPanel()
        val emptySnapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
            emptyList(),
            emptyList(),
            null
        )
        val readySnapshot = emptySnapshot.copy(
            agents = listOf(
                AgentCapability(AiAgent.CURSOR, setOf(AiAgentDetectionSource.IDE), false, true),
                AgentCapability(AiAgent.GITHUB_COPILOT, setOf(AiAgentDetectionSource.IDE), false, false)
            ),
            mcpConfigurations = mapOf(AiAgent.CURSOR to McpAgentConfiguration(
                AiAgent.CURSOR, java.nio.file.Path.of("/tmp/mcp.json"), McpConfigurationKind.NOT_CONFIGURED, emptyList()
            ))
        )
        val states = listOf(
            AiIntegrationsPanelState.Loading to listOf("Checking", "Checking"),
            AiIntegrationsPanelState.Ready(readySnapshot) to listOf("Installed", "IDE setup: 1"),
            AiIntegrationsPanelState.Error("failed") to listOf("Needs attention", "Needs attention"),
            AiIntegrationsPanelState.Ready(emptySnapshot) to listOf("Installed", "IDE setup: 0")
        )

        states.forEach { (state, expectedStatuses) ->
            panel.render(state)
            val statusLabels = descendants(panel).filterIsInstance<JBLabel>()
                .map { it.text }
                .filter { it in setOf("Checking", "Installed", "Needs attention") || it.startsWith("IDE setup: ") }
            assertThat(statusLabels).containsExactlyElementsOf(expectedStatuses)
        }
    }

    @Test
    fun `lays out cards side by side when wide and at natural height`() {
        val panel = AiIntegrationsPanel()
        panel.setSize(AiIntegrationsPanel.WIDE_LAYOUT_THRESHOLD + 200, 1000)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }
        val integrationCards = descendants(panel).filterIsInstance<JPanel>()
            .single { it.layout is GridLayout && it.componentCount == 2 }
        assertThat(integrationCards.components[0].y).isEqualTo(integrationCards.components[1].y)
        assertThat(integrationCards.components[1].x).isGreaterThan(integrationCards.components[0].x)
        val contentColumn = integrationCards.parent
        contentColumn.setSize(contentColumn.width, contentColumn.preferredSize.height + 300)
        layoutRecursively(contentColumn)
        assertThat(integrationCards.height).isPositive()
        assertThat(integrationCards.height).isEqualTo(integrationCards.preferredSize.height)
    }

    @Test
    fun `stacks cards in a narrow tool window`() {
        val panel = AiIntegrationsPanel()
        panel.setSize(500, 600)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }

        val titles = descendants(panel).filterIsInstance<JBLabel>()
            .filter { it.text in setOf("SonarQube CLI", "SonarQube MCP Server") }
        val cliTitle = titles.single { it.text == "SonarQube CLI" }
        val mcpTitle = titles.single { it.text == "SonarQube MCP Server" }
        assertThat(SwingUtilities.convertPoint(mcpTitle, 0, 0, panel).y)
            .isGreaterThan(SwingUtilities.convertPoint(cliTitle, 0, 0, panel).y)
    }

    @Test
    fun `refresh button invokes the refresh callback`() {
        val panel = AiIntegrationsPanel()
        var refreshCount = 0
        panel.setRefreshListener { refreshCount++ }

        val refreshButton = descendants(panel).filterIsInstance<JButton>().first { it.text == "Refresh" }
        assertThat(refreshButton.icon).isNotNull()
        refreshButton.doClick()
        assertThat(refreshCount).isEqualTo(1)
    }

    @Test
    fun `retry and check again buttons use the current refresh listener and detach on disposal`() {
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNKNOWN, "1.0", null, null),
            emptyList(),
            emptyList(),
            null
        )
        val states = listOf(
            AiIntegrationsPanelState.Error("failed") to "Try again",
            AiIntegrationsPanelState.Ready(snapshot) to "Check again"
        )
        states.forEach { (state, label) ->
            val panel = AiIntegrationsPanel()
            var originalCalls = 0
            var replacementCalls = 0
            panel.setRefreshListener { originalCalls++ }
            panel.render(state)
            val button = descendants(panel).filterIsInstance<JButton>().single { it.text == label }

            panel.setRefreshListener { replacementCalls++ }
            button.doClick()
            assertThat(originalCalls).isZero()
            assertThat(replacementCalls).isEqualTo(1)

            panel.dispose()
            button.doClick()
            assertThat(originalCalls).isZero()
            assertThat(replacementCalls).isEqualTo(1)
        }
    }

    @Test
    fun `keeps status pills visible in a narrow tool window`() {
        val panel = AiIntegrationsPanel()
        panel.setSize(420, 600)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }

        val viewport = (panel.components.single() as JScrollPane).viewport
        val view = viewport.view as Container
        assertThat(view.width).isEqualTo(viewport.extentSize.width)
        val statusLabels = descendants(view).filterIsInstance<JBLabel>().filter { it.text == "Checking" }
        assertThat(statusLabels).hasSize(2)
        statusLabels.forEach { label ->
            assertThat(label.width).isPositive()
            assertThat(SwingUtilities.convertPoint(label, label.width, 0, view).x).isLessThanOrEqualTo(view.width)
        }
    }

    @Test
    fun `keeps shorter card content at its natural height in wide layout`() {
        val panel = AiIntegrationsPanel()
        panel.render(AiIntegrationsPanelState.Error("failed"))
        panel.setSize(AiIntegrationsPanel.WIDE_LAYOUT_THRESHOLD + 200, 700)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }

        val cards = descendants(panel).filterIsInstance<JPanel>().first { it.layout is GridLayout && it.componentCount == 2 }
        val mcpCard = cards.components[1] as Container
        val message = descendants(mcpCard).filterIsInstance<JBTextArea>().first { it.text == "MCP capabilities could not be loaded." }

        assertThat(mcpCard.height).isGreaterThan(mcpCard.preferredSize.height)
        assertThat(message.height).isPositive()
        assertThat(message.height).isLessThanOrEqualTo(message.preferredSize.height)
    }

    @Test
    fun `uses the IDE label font for body copy and keeps the status badge compact`() {
        val panel = AiIntegrationsPanel()
        panel.render(AiIntegrationsPanelState.Ready(AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
            emptyList(),
            emptyList(),
            null
        )))
        panel.setSize(AiIntegrationsPanel.WIDE_LAYOUT_THRESHOLD + 200, 700)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }

        val subtitle = descendants(panel).filterIsInstance<JBTextArea>()
            .first { it.text == "Find and fix issues as your agent writes code." }
        assertThat(subtitle.font.family).isEqualTo(JBFont.label().family)

        val cliDescription = descendants(panel).filterIsInstance<JEditorPane>()
            .first { it.text.contains(SonarLintDocumentation.Intellij.SONAR_VORTEX_LINK) }
        val mcpDescription = descendants(panel).filterIsInstance<JBTextArea>()
            .first { it.text.startsWith("Ask your agent about issues") }
        assertThat(cliDescription.foreground).isEqualTo(mcpDescription.foreground)

        val status = descendants(panel).filterIsInstance<JBLabel>().first { it.text == "Installed" }.parent as JPanel
        assertThat(status.height).isEqualTo(status.preferredSize.height)
        assertThat(status.height).isLessThan(status.parent.height)
    }

    @Test
    fun `aligns disclosures and guide links with card text`() {
        val panel = AiIntegrationsPanel()
        panel.render(AiIntegrationsPanelState.Ready(AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
            emptyList(),
            emptyList(),
            null
        )))
        panel.setSize(AiIntegrationsPanel.WIDE_LAYOUT_THRESHOLD + 200, 700)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }

        val cards = descendants(panel).filterIsInstance<JPanel>().first { it.layout is GridLayout && it.componentCount == 2 }
        listOf(
            Triple(cards.components[0] as Container, "SonarQube CLI", "SonarQube CLI guide"),
            Triple(cards.components[1] as Container, "SonarQube MCP Server", "MCP configuration guide")
        ).forEach { (card, title, guideText) ->
            val titleLabel = descendants(card).filterIsInstance<JBLabel>().first { it.text == title }
            val disclosure = descendants(card).filterIsInstance<JToggleButton>().single()
            val guide = descendants(card).filterIsInstance<JButton>().first { it.text == guideText }
            val textLeft = SwingUtilities.convertPoint(titleLabel, 0, 0, card).x

            assertThat(SwingUtilities.convertPoint(disclosure, disclosure.insets.left, 0, card).x).isEqualTo(textLeft)
            assertThat(SwingUtilities.convertPoint(guide, guide.insets.left, 0, card).x).isEqualTo(textLeft)
        }
    }

    @Test
    fun `opens the matching guide from each card`() {
        val openedUrls = mutableListOf<String>()
        val panel = AiIntegrationsPanel(openLink = { openedUrls += it })

        val vortexUrl = SonarLintDocumentation.Intellij.SONAR_VORTEX_LINK
        val cliDescription = descendants(panel).filterIsInstance<JEditorPane>().first { it.text.contains(vortexUrl) }
        val vortexClick = HyperlinkEvent(cliDescription, HyperlinkEvent.EventType.ACTIVATED, null, vortexUrl)
        cliDescription.hyperlinkListeners.forEach { it.hyperlinkUpdate(vortexClick) }

        listOf("SonarQube CLI guide", "MCP configuration guide").forEach { label ->
            descendants(panel).filterIsInstance<JButton>().first { it.text == label }.doClick()
        }

        assertThat(openedUrls).containsExactly(
            vortexUrl,
            SonarLintDocumentation.Intellij.SONARQUBE_CLI_GUIDE_LINK,
            SonarLintDocumentation.Intellij.MCP_CONFIGURATION_GUIDE_LINK
        )
    }

    @Test
    fun `guide links show an external browser icon after the text`() {
        val panel = AiIntegrationsPanel()
        listOf("SonarQube CLI guide", "MCP configuration guide").forEach { label ->
            val guide = descendants(panel).filterIsInstance<JButton>().first { it.text == label }
            assertThat(guide.icon).isSameAs(AllIcons.Ide.External_link_arrow)
            assertThat(guide.horizontalTextPosition).isEqualTo(SwingConstants.LEFT)
        }
    }

    @Test
    fun `keeps the agent inventory collapsed until details are requested`() {
        val panel = AiIntegrationsPanel()
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
            listOf(
                AgentCapability(AiAgent.CURSOR, setOf(AiAgentDetectionSource.IDE), true, false),
                AgentCapability(AiAgent.GITHUB_COPILOT, setOf(AiAgentDetectionSource.IDE), false, true)
            ),
            emptyList(),
            null,
            mcpConfigurations = mapOf(AiAgent.GITHUB_COPILOT to McpAgentConfiguration(
                AiAgent.GITHUB_COPILOT,
                java.nio.file.Path.of("/tmp/mcp.json"),
                McpConfigurationKind.NOT_CONFIGURED,
                emptyList()
            ))
        )

        panel.render(AiIntegrationsPanelState.Ready(snapshot))

        assertThat(labelTexts(panel)).contains("2 agents detected").doesNotContain("Not supported")
        val disclosures = descendants(panel).filterIsInstance<JToggleButton>()
        assertThat(disclosures).hasSize(2)
        val cliDisclosure = disclosures[0]
        val mcpDisclosure = disclosures[1]
        val cliCard = cliDisclosure.parent.parent as Container
        val mcpCard = mcpDisclosure.parent.parent as Container

        cliDisclosure.doClick()
        assertThat(descendants(panel).filterIsInstance<JToggleButton>()[0]).isSameAs(cliDisclosure)
        assertThat(cliDisclosure.isSelected).isTrue()
        assertThat(cliDisclosure.text).startsWith("▾")
        assertThat(labelTexts(cliCard)).containsSubsequence("Cursor", "Supported", "GitHub Copilot", "Not supported")
        assertThat(labelTexts(mcpCard)).doesNotContain("Not supported")

        mcpDisclosure.doClick()
        assertThat(labelTexts(mcpCard)).containsSubsequence("Cursor", "Available through SonarQube CLI", "GitHub Copilot", "Not configured")

        cliDisclosure.doClick()
        assertThat(labelTexts(cliCard)).doesNotContain("Not supported")
        assertThat(cliDisclosure.isSelected).isFalse()
        assertThat(cliDisclosure.text).startsWith("▸")
        assertThat(mcpDisclosure.isSelected).isTrue()
    }

    @Test
    fun `shows recording badges for detected CLI agents independently of authentication and standalone MCP`() {
        val panel = AiIntegrationsPanel()
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNAUTHENTICATED, "1.0", null, null),
            listOf(
                AgentCapability(AiAgent.CLAUDE_CODE, setOf(AiAgentDetectionSource.CLI), true, false),
                AgentCapability(AiAgent.CODEX, setOf(AiAgentDetectionSource.CLI), true, true),
                AgentCapability(AiAgent.KIRO, setOf(AiAgentDetectionSource.CLI), true, false),
                AgentCapability(AiAgent.WINDSURF, setOf(AiAgentDetectionSource.CLI), true, false)
            ),
            emptyList(),
            null,
            listOf(
                AgentCliIntegration(AiAgent.CLAUDE_CODE, CliIntegrationRecordingStatus.RECORDED, emptyList()),
                AgentCliIntegration(AiAgent.CODEX, CliIntegrationRecordingStatus.NOT_RECORDED, emptyList()),
                AgentCliIntegration(AiAgent.KIRO, CliIntegrationRecordingStatus.UNKNOWN, emptyList()),
                AgentCliIntegration(AiAgent.GITHUB_COPILOT_CLI, CliIntegrationRecordingStatus.RECORDED, emptyList())
            )
        )
        panel.render(AiIntegrationsPanelState.Ready(snapshot))
        val disclosures = descendants(panel).filterIsInstance<JToggleButton>()
        val cliCard = disclosures[0].parent.parent as Container
        val mcpCard = disclosures[1].parent.parent as Container
        disclosures.forEach { it.doClick() }

        assertThat(labelTexts(cliCard).filter { it in setOf("Integration recorded", "No integration recorded", "Unknown") })
            .containsExactly("Integration recorded", "No integration recorded", "Unknown", "Unknown")
        assertThat(labelTexts(cliCard)).doesNotContain("GitHub Copilot CLI")
        assertThat(labelTexts(cliCard).any { it.contains("Not signed in") }).isTrue()
        assertThat(labelTexts(mcpCard)).containsSubsequence("Claude Code", "Available through SonarQube CLI", "Codex", "Available through SonarQube CLI")
            .doesNotContain("Integration recorded", "No integration recorded", "Unknown")
    }

    @Test
    fun `expands configuration paths and only the reported checks while preserving duplicates`() {
        val panel = AiIntegrationsPanel()
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
            listOf(AgentCapability(AiAgent.CLAUDE_CODE, setOf(AiAgentDetectionSource.CLI), true, true)),
            emptyList(),
            null,
            listOf(AgentCliIntegration(AiAgent.CLAUDE_CODE, CliIntegrationRecordingStatus.RECORDED, listOf(
                CliIntegrationConfig("/config.json", CliIntegrationCheckStatus.CONFIGURED, CliIntegrationCheckStatus.INVALID),
                CliIntegrationConfig("/config.json", CliIntegrationCheckStatus.NOT_CONFIGURED, CliIntegrationCheckStatus.UNKNOWN),
                CliIntegrationConfig(null, null, CliIntegrationCheckStatus.CONFIGURED),
                CliIntegrationConfig(null, null, null)
            )))
        )
        panel.render(AiIntegrationsPanelState.Ready(snapshot))
        descendants(panel).filterIsInstance<JToggleButton>().first().doClick()
        val configurationDisclosure = descendants(panel).filterIsInstance<JToggleButton>()
            .single { it.text.endsWith("Configuration details") }
        val intents = mutableListOf<AiIntegrationsIntent>()
        panel.setIntentListener { intents += it }
        descendants(panel).filterIsInstance<JButton>().single { it.text == "Integrate" }.doClick()
        assertThat(intents).containsExactly(AiIntegrationsIntent.IntegrateCli(AiAgent.CLAUDE_CODE))
        assertThat(labelTexts(panel)).contains("Integration recorded")
        assertThat(descendants(panel).filterIsInstance<JBTextArea>().map { it.text }).doesNotContain("/config.json")

        configurationDisclosure.doClick()

        val paths = descendants(panel).filterIsInstance<JBTextArea>().map { it.text }
            .filter { it in setOf("/config.json", "Configuration path not reported") }
        assertThat(paths).containsExactly("/config.json", "/config.json", "Configuration path not reported", "Configuration path not reported")
        assertThat(labelTexts(panel).filter { it.startsWith("MCP:") || it.startsWith("Hooks:") }).containsExactly(
            "MCP: Configured", "Hooks: Invalid configuration", "MCP: Not configured", "Hooks: Unknown", "Hooks: Configured"
        )
        panel.setSize(500, 1000)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }
        val agentRow = configurationDisclosure.parent.parent as JPanel
        assertThat(agentRow.maximumSize.height).isEqualTo(agentRow.preferredSize.height)
        assertThat(agentRow.height).isEqualTo(agentRow.preferredSize.height)

        panel.render(AiIntegrationsPanelState.Ready(snapshot.copy(cli = snapshot.cli.copy(authentication = CliAuthenticationStatus.INVALID))))
        assertThat(descendants(panel).filterIsInstance<JToggleButton>().single { it.text.endsWith("Configuration details") }.isSelected).isTrue()
        assertThat(labelTexts(panel)).contains("Integration recorded", "MCP: Configured")
        assertThat(descendants(panel).filterIsInstance<JButton>().map { it.text }).doesNotContain("Integrate")

        descendants(panel).filterIsInstance<JToggleButton>().single { it.text.endsWith("Configuration details") }.doClick()
        assertThat(descendants(panel).filterIsInstance<JBTextArea>().map { it.text }).doesNotContain("/config.json")
    }

    @Test
    fun `renders hosted agents before CLI discoveries with backend capabilities`() {
        val panel = AiIntegrationsPanel()
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
            listOf(
                AgentCapability(AiAgent.GITHUB_COPILOT, setOf(AiAgentDetectionSource.IDE), false, true),
                AgentCapability(AiAgent.JUNIE, setOf(AiAgentDetectionSource.IDE), false, true),
                AgentCapability(AiAgent.JETBRAINS_AI_ASSISTANT, setOf(AiAgentDetectionSource.IDE), false, true),
                AgentCapability(AiAgent.CLAUDE_CODE, setOf(AiAgentDetectionSource.CLI), true, true),
                AgentCapability(AiAgent.CODEX, setOf(AiAgentDetectionSource.CLI), true, false)
            ),
            emptyList(),
            null
        )
        panel.render(AiIntegrationsPanelState.Ready(snapshot))
        val disclosures = descendants(panel).filterIsInstance<JToggleButton>()
        disclosures.forEach { it.doClick() }
        val cliCard = disclosures[0].parent.parent as Container
        val mcpCard = disclosures[1].parent.parent as Container

        assertThat(labelTexts(cliCard)).containsSubsequence(
            "GitHub Copilot", "Not supported", "Junie", "Not supported", "JetBrains AI Assistant", "Not supported",
            "Claude Code", "Supported", "Codex", "Supported"
        )
        assertThat(labelTexts(mcpCard)).containsSubsequence(
            "GitHub Copilot", "IDE setup unavailable", "Junie", "IDE setup unavailable", "JetBrains AI Assistant", "IDE setup unavailable",
            "Claude Code", "Available through SonarQube CLI", "Codex", "Available through SonarQube CLI"
        )

        panel.render(AiIntegrationsPanelState.Ready(snapshot.copy(agents = snapshot.agents.take(3).map {
            it.copy(standaloneMcpSupported = false)
        })))
        val updatedMcpCard = descendants(panel).filterIsInstance<JToggleButton>()[1].parent.parent as Container
        assertThat(labelTexts(updatedMcpCard)).contains("IDE setup: 0").containsSubsequence(
            "GitHub Copilot", "IDE setup unavailable", "Junie", "IDE setup unavailable", "JetBrains AI Assistant", "IDE setup unavailable"
        ).doesNotContain("Supported")
    }

    @Test
    fun `reveals compact MCP actions and inline diagnostics for standalone agents`() {
        val panel = AiIntegrationsPanel()
        panel.render(AiIntegrationsPanelState.Error("failed"))
        val warningColor = descendants(panel).filterIsInstance<JBLabel>().first { it.text == "Needs attention" }.foreground
        val path = java.nio.file.Path.of("/tmp/mcp.json")
        val configurations = listOf(
            McpAgentConfiguration(AiAgent.CURSOR, path, McpConfigurationKind.NOT_CONFIGURED, emptyList()),
            McpAgentConfiguration(AiAgent.GITHUB_COPILOT, path, McpConfigurationKind.STANDALONE, emptyList()),
            McpAgentConfiguration(AiAgent.CLAUDE_CODE, path, McpConfigurationKind.STANDALONE, emptyList()),
            McpAgentConfiguration(AiAgent.KIRO, path, McpConfigurationKind.CLI_MANAGED, emptyList()),
            McpAgentConfiguration(AiAgent.CODEX, path, McpConfigurationKind.UNKNOWN, listOf("inspect manually")),
            McpAgentConfiguration(AiAgent.WINDSURF, path, McpConfigurationKind.MALFORMED, listOf("invalid json"))
        ).associateBy { it.agent }
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, null, null, null),
            configurations.keys.map { AgentCapability(it, emptySet(), false, true) } +
                AgentCapability(AiAgent.GITHUB_COPILOT_CLI, setOf(AiAgentDetectionSource.CLI), true, false),
            emptyList(),
            null,
            mcpConfigurations = configurations
        )

        var lastIntent: AiIntegrationsIntent? = null
        panel.setIntentListener { lastIntent = it }
        panel.render(AiIntegrationsPanelState.Ready(snapshot))

        val status = descendants(panel).filterIsInstance<JBLabel>().single { it.text == "IDE setup: 6" }
        assertThat(status.foreground).isEqualTo(warningColor)
        assertThat(labelTexts(panel)).contains("2 configurations need attention.")
        assertThat(labelTexts(panel)).doesNotContain("Not configured", "Invalid configuration")
        assertThat(descendants(panel).filterIsInstance<JButton>().map { it.text })
            .doesNotContain("Set up an agent…", "Set up Cursor", "Set up")
        val disclosure = descendants(panel).filterIsInstance<JToggleButton>().last()
        disclosure.doClick()
        assertThat(descendants(panel).filterIsInstance<JToggleButton>().last()).isSameAs(disclosure)
        assertThat(disclosure.isSelected).isTrue()

        val buttonLabels = descendants(panel).filterIsInstance<JButton>().map { it.text }
        val text = descendants(panel).filter { it is JBLabel || it is JBTextArea }.joinToString(" ") {
            when (it) {
                is JBLabel -> it.text
                is JBTextArea -> it.text
                else -> ""
            }
        }
        assertThat(buttonLabels).contains("Set up", "Open")
            .doesNotContain("Replace…", "Integrate with CLI", "CLI", "Use CLI")
        assertThat(text).contains("Configured", "Managed by CLI", "Status unknown", "Invalid configuration", "inspect manually", "invalid json")
            .doesNotContain("Connection not verified", "Configured externally", "Available through CLI")

        descendants(panel).filterIsInstance<JButton>().first { it.text == "Set up" }.doClick()
        assertThat(lastIntent).isEqualTo(AiIntegrationsIntent.SetUpMcp(AiAgent.CURSOR))
        descendants(panel).filterIsInstance<JButton>().first { it.text == "Open" }.doClick()
        assertThat(lastIntent).isEqualTo(AiIntegrationsIntent.OpenMcpConfiguration(AiAgent.GITHUB_COPILOT))

        disclosure.doClick()
        assertThat(disclosure.isSelected).isFalse()
        assertThat(labelTexts(panel)).doesNotContain("Invalid configuration")
    }

    @Test
    fun `MCP inventory shows all five detected agents and offers CLI setup without inflating configured totals`() {
        val panel = AiIntegrationsPanel()
        val configured = McpAgentConfiguration(
            AiAgent.CURSOR, java.nio.file.Path.of("/tmp/mcp.json"), McpConfigurationKind.STANDALONE, emptyList()
        )
        val claude = configured.copy(agent = AiAgent.CLAUDE_CODE, state = McpConfigurationKind.CLI_MANAGED)
        val cliAgents = listOf(AiAgent.CODEX, AiAgent.GITHUB_COPILOT_CLI, AiAgent.ANTIGRAVITY)
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, null, null, null),
            listOf(
                AgentCapability(AiAgent.CURSOR, emptySet(), false, true),
                AgentCapability(AiAgent.CLAUDE_CODE, emptySet(), true, true)
            ) + cliAgents.map { AgentCapability(it, emptySet(), true, false) },
            emptyList(),
            null,
            mcpConfigurations = mapOf(configured.agent to configured, claude.agent to claude)
        )

        var lastIntent: AiIntegrationsIntent? = null
        panel.setIntentListener { lastIntent = it }
        panel.render(AiIntegrationsPanelState.Ready(snapshot))
        assertThat(labelTexts(panel)).contains("5 agents detected", "IDE setup: 2", "MCP configured for 2 of 2 agents with IDE setup.")
            .doesNotContain("MCP is configured for all 2 detected agents.")
        assertThat(descendants(panel).filterIsInstance<JButton>().map { it.text }).doesNotContain("Set up an agent…")
        descendants(panel).filterIsInstance<JToggleButton>().last().doClick()
        val mcpCard = descendants(panel).filterIsInstance<JToggleButton>().last().parent.parent as Container
        assertThat(labelTexts(mcpCard)).contains("Cursor", "Claude Code", "Codex", "GitHub Copilot CLI", "Antigravity")
        val cliButtons = descendants(mcpCard).filterIsInstance<JButton>().filter { it.text == "Set up via CLI" }
        assertThat(cliButtons).hasSize(3)
        cliButtons.forEachIndexed { index, button ->
            button.doClick()
            assertThat(lastIntent).isEqualTo(AiIntegrationsIntent.IntegrateCli(cliAgents[index]))
        }

        panel.render(AiIntegrationsPanelState.Ready(snapshot.copy(mcpConfigurations = mapOf(
            configured.agent to configured.copy(state = McpConfigurationKind.NOT_CONFIGURED),
            claude.agent to claude
        ))))
        assertThat(labelTexts(panel)).contains("MCP configured for 1 of 2 agents with IDE setup.")
        assertThat(descendants(panel).filterIsInstance<JButton>().map { it.text })
            .contains("Set up").doesNotContain("Set up Cursor", "Set up an agent…")

        panel.render(AiIntegrationsPanelState.Ready(snapshot.copy(
            cli = snapshot.cli.copy(authentication = CliAuthenticationStatus.UNAUTHENTICATED),
            agents = snapshot.agents.filter { it.agent in cliAgents },
            mcpConfigurations = emptyMap()
        )))
        assertThat(labelTexts(panel)).contains("IDE setup: 0", "Codex", "GitHub Copilot CLI", "Antigravity")
        assertThat(descendants(panel).filterIsInstance<JButton>().map { it.text }).doesNotContain("Set up via CLI")
    }

    private fun labelTexts(container: Container) = descendants(container).filterIsInstance<JBLabel>().map { it.text }

    private fun layoutRecursively(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layoutRecursively)
    }

    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { component ->
        listOf(component) + if (component is Container) descendants(component) else emptyList()
    }
}
