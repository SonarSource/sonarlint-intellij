/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.EnumSet
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.DocumentEvent
import kotlin.properties.Delegates
import org.apache.commons.text.StringEscapeUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.ui.options.OptionPanel
import org.sonarlint.intellij.ui.options.addComponents
import org.sonarsource.sonarlint.core.client.utils.DependencyRiskTransitionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition.ACCEPT
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition.CONFIRM
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition.REOPEN
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition.SAFE
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

val TRANSITIONS_WITH_OPTIONAL_COMMENT: EnumSet<DependencyRiskTransition> = EnumSet.of(CONFIRM, REOPEN)

private const val ADD_COMMENT_TEXT = "Add a comment"
private const val ADD_REQUIRED_COMMENT_TEXT = "$ADD_COMMENT_TEXT (Required)"

private val possibleOptions = listOf(
    DependencyRiskTransitionStatus.REOPEN,
    DependencyRiskTransitionStatus.CONFIRM,
    DependencyRiskTransitionStatus.ACCEPT,
    DependencyRiskTransitionStatus.SAFE,
)

class ChangeDependencyRiskStatusPanel(
    private val connection: ServerConnection,
    initialStatus: DependencyRiskDto.Status,
    private val allowedTransitions: List<DependencyRiskTransitionStatus>,
    private val callbackForButton: (Boolean) -> Unit,
) : JPanel(), ActionListener {
    var selectedStatus: DependencyRiskTransition? by Delegates.observable(null) { _, _, newValue -> callbackForButton(isReady(newValue)) }

    private lateinit var commentTextArea : JBTextArea
    private lateinit var commentLabel: JBLabel

    private val initialSelection: DependencyRiskTransitionStatus = when (initialStatus) {
        DependencyRiskDto.Status.FIXED -> DependencyRiskTransitionStatus.FIXED
        DependencyRiskDto.Status.OPEN -> DependencyRiskTransitionStatus.REOPEN
        DependencyRiskDto.Status.CONFIRM -> DependencyRiskTransitionStatus.CONFIRM
        DependencyRiskDto.Status.ACCEPT -> DependencyRiskTransitionStatus.ACCEPT
        DependencyRiskDto.Status.SAFE -> DependencyRiskTransitionStatus.SAFE
    }

    init {
        layout = verticalLayout()
        display()
    }

    fun display() {
        val buttonGroup = ButtonGroup()
        possibleOptions.forEach { transition ->
            addNewStatus(transition, buttonGroup)
        }
        add(commentPanel())
    }

    private fun addNewStatus(
        transition: DependencyRiskTransitionStatus,
        buttonGroup: ButtonGroup,
    ) {
        val statusPanel = buildStatusPanel(transition)
        add(statusPanel)
        addComponents(buttonGroup, statusPanel)
    }

    private fun buildStatusPanel(transition: DependencyRiskTransitionStatus): OptionPanel {
        return OptionPanel(
            transition.name,
            transition.title,
            transition.description
        ).also {
            it.statusRadioButton.addActionListener(this)
            it.statusRadioButton.actionCommand = transition.name
            it.isEnabled = transition in allowedTransitions
            it.setSelected(transition == initialSelection)
        }
    }

    fun getComment() : String? {
        return commentTextArea.text.ifBlank { null }
    }

    private fun commentPanel(): JPanel {
        commentTextArea = JBTextArea()
        commentTextArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                callbackForButton(isReady())
            }
        })
        commentLabel = JBLabel(ADD_COMMENT_TEXT)
        return JPanel(verticalLayout()).apply {
            add(commentLabel)
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
    private fun isReady(newValue: DependencyRiskTransition?): Boolean {
        return toTransitionChangeEnum(newValue) in allowedTransitions
            && isReady()
    }

    private fun toTransitionChangeEnum(value: DependencyRiskTransition?): DependencyRiskTransitionStatus? {
        return when (value) {
            REOPEN -> DependencyRiskTransitionStatus.REOPEN
            CONFIRM -> DependencyRiskTransitionStatus.CONFIRM
            ACCEPT -> DependencyRiskTransitionStatus.ACCEPT
            SAFE -> DependencyRiskTransitionStatus.SAFE
            else -> null
        }
    }

    private fun isReady() = isCommentOptional() || getComment() != null

    fun isCommentOptional() = TRANSITIONS_WITH_OPTIONAL_COMMENT.contains(selectedStatus)

    private fun verticalLayout() = VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 15, true, false)

    override fun actionPerformed(e: ActionEvent) {
        selectedStatus = DependencyRiskTransition.valueOf(e.actionCommand)
        commentLabel.text = chooseLabelText()
    }

    private fun chooseLabelText(): String {
        return if (isCommentOptional())
            ADD_COMMENT_TEXT
        else
            ADD_REQUIRED_COMMENT_TEXT
    }
}
