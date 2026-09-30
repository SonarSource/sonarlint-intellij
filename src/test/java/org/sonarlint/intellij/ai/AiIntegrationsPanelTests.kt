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
import com.intellij.ui.components.JBTextArea
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

        panel.setSize(AiIntegrationsPanel.WIDE_LAYOUT_THRESHOLD + 200, 1000)
        panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
        repeat(2) { layoutRecursively(panel) }
        assertThat(panel.isWideLayout()).isTrue()
        assertThat(panel.integrationCardsAreSideBySide()).isTrue()
        val integrationCards = descendants(panel).filterIsInstance<JPanel>()
            .single { it.layout is GridLayout && it.componentCount == 2 }
        val contentColumn = integrationCards.parent
        contentColumn.setSize(contentColumn.width, contentColumn.preferredSize.height + 300)
        layoutRecursively(contentColumn)
        assertThat(integrationCards.height).isPositive()
        assertThat(integrationCards.height).isEqualTo(integrationCards.preferredSize.height)
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
    }

    private fun layoutRecursively(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layoutRecursively)
    }

    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { component ->
        listOf(component) + if (component is Container) descendants(component) else emptyList()
    }
}
