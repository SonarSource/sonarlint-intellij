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
package org.sonarlint.intellij.ui.page

import com.intellij.ui.components.JBLabel
import java.awt.Container
import javax.swing.JButton
import org.sonarlint.intellij.ai.AiIntegrationsPanel
import org.sonarlint.intellij.ai.CLI_CARD_TITLE
import org.sonarlint.intellij.ai.MCP_CARD_TITLE
import org.sonarlint.intellij.ai.REFRESH_LABEL
import org.sonarlint.intellij.fixtures.findComponents

class AiIntegrationsTabPage internal constructor(
    private val panel: AiIntegrationsPanel,
) {

    val hasSonarQubeCliCard: Boolean
        get() = findTitleLabel(CLI_CARD_TITLE) != null

    val hasSonarQubeMcpServerCard: Boolean
        get() = findTitleLabel(MCP_CARD_TITLE) != null

    val sonarQubeCliCard: AiIntegrationCardPage
        get() = AiIntegrationCardPage { findCard(CLI_CARD_TITLE, otherCardTitle = MCP_CARD_TITLE) }

    val sonarQubeMcpServerCard: AiIntegrationCardPage
        get() = AiIntegrationCardPage { findCard(MCP_CARD_TITLE, otherCardTitle = CLI_CARD_TITLE) }

    fun refresh() {
        val button = panel.findComponents<JButton>().firstOrNull { it.text == REFRESH_LABEL }
        checkNotNull(button) { "No '$REFRESH_LABEL' button in the AI Integrations tab" }
        button.doClick()
    }

    private fun findTitleLabel(title: String): JBLabel? =
        panel.findComponents<JBLabel>().firstOrNull { it.text == title }

    /** A card is the largest container around its title that does not also hold the other card. */
    private fun findCard(title: String, otherCardTitle: String): Container {
        var card = checkNotNull(findTitleLabel(title)) { "No '$title' card in the AI Integrations tab" }.parent
        while (card.parent != null && card.parent != panel &&
            card.parent.findComponents<JBLabel>().none { it.text == otherCardTitle }
        ) {
            card = card.parent
        }
        return card
    }
}
