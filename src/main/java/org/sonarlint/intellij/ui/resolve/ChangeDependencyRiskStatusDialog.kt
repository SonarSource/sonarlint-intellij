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

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.event.ActionEvent
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.ui.UiUtils
import org.sonarsource.sonarlint.core.client.utils.DependencyRiskTransitionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition

class ChangeDependencyRiskStatusDialog(
    project: Project,
    connection: ServerConnection,
    availableTransitions: List<DependencyRiskTransitionStatus>,
) : DialogWrapper(false) {

    private val centerPanel: ChangeDependencyRiskStatusPanel
    private val changeStatusAction: DialogWrapperAction

    init {
        title = "Change Dependency Risk Status on ${connection.productName}"
        isResizable = false
        changeStatusAction = object : DialogWrapperAction("Change Status to\u2026") {
            init {
                putValue(DEFAULT_ACTION, true)
                isEnabled = false
            }

            override fun doAction(e: ActionEvent) {
                val status = getStatus()

                if (status != null) {
                    closeDialog(project)
                }
            }
        }
        centerPanel = ChangeDependencyRiskStatusPanel(connection, availableTransitions) { changeStatusAction.isEnabled = it }
        init()
    }

    fun chooseStatusChange() = if (showAndGet()) StatusChange(getStatus()!!, getComment()) else null

    private fun getStatus() = centerPanel.selectedStatus
    private fun getComment() = centerPanel.getComment()

    override fun createCenterPanel() = centerPanel

    override fun createActions() = arrayOf(changeStatusAction, cancelAction)

    override fun getPreferredFocusedComponent() = getButton(cancelAction)

    private fun closeDialog(project: Project) {
        UiUtils.runOnUiThread(
            project,
            ModalityState.stateForComponent(this@ChangeDependencyRiskStatusDialog.contentPane)
        ) { close(OK_EXIT_CODE) }
    }

    data class StatusChange(val newStatus: DependencyRiskTransition, val comment: String?)
} 
