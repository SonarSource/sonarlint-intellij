/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.ui.resolve

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.properties.Delegates
import org.apache.commons.text.StringEscapeUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.ui.options.OptionPanel
import org.sonarlint.intellij.ui.options.addComponents
import org.sonarsource.sonarlint.core.client.utils.DependencyRiskTransitionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition


class ChangeDependencyRiskStatusPanel(
    private val connection: ServerConnection,
    allowedTransitions: List<DependencyRiskTransitionStatus>,
    private val callbackForButton: (Boolean) -> Unit,
) : JPanel(), ActionListener {
    var selectedStatus: DependencyRiskTransition? by Delegates.observable(null) { _, _, newValue -> callbackForButton(newValue != null) }
    private lateinit var commentTextArea : JBTextArea

    init {
        layout = verticalLayout()
        display(allowedTransitions)
    }

    fun display(allowedTransitions: List<DependencyRiskTransitionStatus>) {
        val buttonGroup = ButtonGroup()
        allowedTransitions.forEach { transition ->
            val statusPanel = OptionPanel(
                transition.name,
                transition.title,
                transition.description
            )
            addComponents(buttonGroup, statusPanel)
            statusPanel.statusRadioButton.addActionListener(this)
            statusPanel.statusRadioButton.actionCommand = transition.name
            add(statusPanel)
        }
        add(commentPanel())
    }

    fun getComment() : String? {
        return commentTextArea.text.ifBlank { null }
    }

    private fun commentPanel(): JPanel {
        commentTextArea = JBTextArea()
        return JPanel(verticalLayout()).apply {
            add(JBLabel("Add a comment (optional)"))
            add(
                JScrollPane(
                    commentTextArea.apply {
                        rows = 3
                    })
            )
            val link = StringEscapeUtils.escapeHtml4(connection.links().formattingSyntaxDoc())
            add(JBLabel("<a href=\"$link\">Formatting Help</a>:  *Bold*  ``Code``  * Bulleted point").apply { setCopyable(true) })
        }
    }

    private fun verticalLayout() = VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 15, true, false)

    override fun actionPerformed(e: ActionEvent?) {
        e ?: return
        selectedStatus = DependencyRiskTransition.valueOf(e.actionCommand)
    }

} 
