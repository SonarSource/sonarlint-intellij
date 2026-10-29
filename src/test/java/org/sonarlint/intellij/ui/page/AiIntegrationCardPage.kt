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

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import java.awt.Container
import javax.swing.JToggleButton
import org.sonarlint.intellij.ai.MANAGE_AGENTS_LABEL
import org.sonarlint.intellij.ai.UNINSTALL_CLI_LABEL
import org.sonarlint.intellij.fixtures.findComponents

class AiIntegrationCardPage internal constructor(
    findCard: () -> Container,
) : AiIntegrationsPartPage(findCard) {

    val documentationLinks: List<String>
        get() = findPart().findComponents<ActionLink>().map { it.text }

    val hasUninstallAction: Boolean
        get() = uninstallButton() != null

    fun uninstall() {
        val button = checkNotNull(uninstallButton()) { "No '$UNINSTALL_CLI_LABEL' action in the card" }
        check(button.presentation.isEnabled) { "'$UNINSTALL_CLI_LABEL' is disabled" }
        button.click()
    }

    /** Expands "Manage agents" so the agent rows are shown. */
    fun showAgents() {
        val toggle = findPart().findComponents<JToggleButton>().firstOrNull { it.text.endsWith(MANAGE_AGENTS_LABEL) }
        checkNotNull(toggle) { "No '$MANAGE_AGENTS_LABEL' toggle in the card" }
        if (!toggle.isSelected) {
            toggle.doClick()
        }
    }

    fun hasAgent(name: String): Boolean = findAgentNameLabel(name) != null

    /** The row of an agent, e.g. "Claude Code"; needs [showAgents]. */
    fun agent(name: String): AiIntegrationsPartPage = AiIntegrationsPartPage {
        // The name sits in a column of the row, next to the row's status and actions
        checkNotNull(findAgentNameLabel(name)) { "No '$name' agent in the card, found $labels" }.parent.parent
    }

    private fun findAgentNameLabel(name: String): JBLabel? = findPart().findComponents<JBLabel>().firstOrNull { it.text == name }

    private fun uninstallButton(): ActionButton? =
        findPart().findComponents<ActionButton>().firstOrNull { it.accessibleContext.accessibleName == UNINSTALL_CLI_LABEL }
}
