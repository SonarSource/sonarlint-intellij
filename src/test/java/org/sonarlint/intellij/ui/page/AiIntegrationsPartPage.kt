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

import com.intellij.icons.AllIcons
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import java.awt.Container
import javax.swing.JButton
import org.sonarlint.intellij.fixtures.findComponents

/**
 * Labels and buttons of one part of the AI Integrations tab. The panel rebuilds its content on every state change,
 * so the part is looked up again on each read.
 */
open class AiIntegrationsPartPage internal constructor(
    protected val findPart: () -> Container,
) {

    val labels: List<String>
        get() = findPart().findComponents<JBLabel>().map { it.text }

    /** Labels rendered with the success tick, e.g. the "Installed" status. */
    val tickedLabels: List<String>
        get() = findPart().findComponents<JBLabel>().filter { it.icon == AllIcons.General.InspectionsOK }.map { it.text }

    /** Action buttons, e.g. "Install SonarQube CLI"; documentation links are excluded. */
    val buttons: List<String>
        get() = actionButtons().map { it.text }

    fun clickButton(text: String) {
        val button = actionButtons().firstOrNull { it.text == text }
        checkNotNull(button) { "No '$text' button, found $buttons" }
        check(button.isEnabled) { "Button '$text' is disabled" }
        button.doClick()
    }

    private fun actionButtons(): List<JButton> = findPart().findComponents<JButton>().filter { it !is ActionLink }
}
