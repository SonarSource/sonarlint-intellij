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
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.text.View
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus

class AiIntegrationsPanelTests : AbstractSonarLintLightTests() {
    @Test
    fun `renders every top level state and changes layout at the logical threshold`() {
        val panel = AiIntegrationsPanel()
        val snapshot = AiIntegrationSnapshot(
            CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
            emptyList(),
            emptyList(),
            null
        )
        val states = listOf(
            AiIntegrationsPanelState.Loading,
            AiIntegrationsPanelState.Ready(snapshot),
            AiIntegrationsPanelState.Error("failed"),
            AiIntegrationsPanelState.Empty(snapshot)
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
        descendants(view).filterIsInstance<JBTextArea>().forEach { textArea ->
            assertThat(textArea.width).isPositive()
            assertThat(SwingUtilities.convertPoint(textArea, textArea.width, 0, view).x).isLessThanOrEqualTo(view.width)
        }
        descendants(view).filterIsInstance<JEditorPane>().forEach { description ->
            assertThat(description.width).isPositive()
            assertThat(SwingUtilities.convertPoint(description, description.width, 0, view).x).isLessThanOrEqualTo(view.width)
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
        val panel = AiIntegrationsPanel()
        val intents = mutableListOf<AiIntegrationsIntent>()
        panel.setIntentListener { intent -> intents += intent }

        val cliDescription = descendants(panel).filterIsInstance<JEditorPane>().first { it.text.contains("SonarVortex") }
        val vortexClick = HyperlinkEvent(cliDescription, HyperlinkEvent.EventType.ACTIVATED, null, "#SonarVortex")
        cliDescription.hyperlinkListeners.forEach { it.hyperlinkUpdate(vortexClick) }

        listOf("SonarQube CLI guide", "MCP configuration guide").forEach { label ->
            descendants(panel).filterIsInstance<JButton>().first { it.text == label }.doClick()
        }

        assertThat(intents).containsExactly(
            AiIntegrationsIntent.OpenVortexDocumentation,
            AiIntegrationsIntent.OpenCliDocumentation,
            AiIntegrationsIntent.OpenMcpDocumentation
        )
    }

    @Test
    fun `external documentation links show a browser icon after the text`() {
        val panel = AiIntegrationsPanel()
        val cliDescription = descendants(panel).filterIsInstance<JEditorPane>().first { it.text.contains("SonarVortex") }
        cliDescription.setSize(500, 100)
        val graphics = BufferedImage(500, 100, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            cliDescription.paint(graphics)
        } finally {
            graphics.dispose()
        }

        assertThat(htmlViews(cliDescription.ui.getRootView(cliDescription)).map { it.javaClass.simpleName })
            .contains("JBIconView")
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
                AgentCapability(AiAgent.CURSOR, setOf(AiAgentDetectionSource.IDE), true, true),
                AgentCapability(AiAgent.GITHUB_COPILOT, setOf(AiAgentDetectionSource.IDE), false, false)
            ),
            emptyList(),
            null
        )

        panel.render(AiIntegrationsPanelState.Ready(snapshot))

        assertThat(labelTexts(panel)).contains("2 agents detected").doesNotContain("Not supported")
        val disclosure = descendants(panel).filterIsInstance<JToggleButton>().first { it.text.contains("Manage agents") }
        assertThat(disclosure.isSelected).isFalse()
        assertThat(disclosure.text).startsWith("▸")
        disclosure.doClick()
        assertThat(labelTexts(panel)).contains("Supported", "Not supported")
        val expandedDisclosure = descendants(panel).filterIsInstance<JToggleButton>().first { it.text.contains("Manage agents") }
        assertThat(expandedDisclosure.isSelected).isTrue()
        assertThat(expandedDisclosure.text).startsWith("▾")
    }

    private fun labelTexts(container: Container) = descendants(container).filterIsInstance<JBLabel>().map { it.text }

    private fun htmlViews(view: View): List<View> = listOf(view) + (0 until view.viewCount).flatMap { htmlViews(view.getView(it)) }

    private fun layoutRecursively(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layoutRecursively)
    }

    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { component ->
        listOf(component) + if (component is Container) descendants(component) else emptyList()
    }
}
