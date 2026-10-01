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

import com.intellij.ui.components.JBLabel
import java.awt.Container
import java.awt.GridLayout
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus

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
        val readySnapshot = emptySnapshot.copy(agents = listOf(
            AgentCapability(AiAgent.CURSOR, setOf(AiAgentDetectionSource.IDE), false, true),
            AgentCapability(AiAgent.GITHUB_COPILOT, setOf(AiAgentDetectionSource.IDE), false, false)
        ))
        val states = listOf(
            AiIntegrationsPanelState.Loading to listOf("Checking", "Checking"),
            AiIntegrationsPanelState.Ready(readySnapshot) to listOf("Installed", "1 supported"),
            AiIntegrationsPanelState.Error("failed") to listOf("Needs attention", "Needs attention"),
            AiIntegrationsPanelState.Empty(emptySnapshot) to listOf("Installed", "0 supported")
        )

        states.forEach { (state, expectedStatuses) ->
            panel.render(state)
            val statusLabels = descendants(panel).filterIsInstance<JBLabel>()
                .map { it.text }
                .filter { it in setOf("Checking", "Installed", "Needs attention") || it.endsWith(" supported") }
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
    fun `refresh button emits an intent`() {
        val panel = AiIntegrationsPanel()
        var emittedIntent: AiIntegrationsIntent? = null
        panel.setIntentListener { emittedIntent = it }

        val refreshButton = descendants(panel).filterIsInstance<JButton>().first { it.text == "Refresh" }
        assertThat(refreshButton.icon).isNotNull()
        refreshButton.doClick()
        assertThat(emittedIntent).isEqualTo(AiIntegrationsIntent.Refresh)
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

    private fun layoutRecursively(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layoutRecursively)
    }

    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { component ->
        listOf(component) + if (component is Container) descendants(component) else emptyList()
    }
}
