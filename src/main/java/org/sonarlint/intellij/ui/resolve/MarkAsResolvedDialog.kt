/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.ui.DialogWrapper
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus
import java.awt.event.ActionEvent

class MarkAsResolvedDialog (
    productName: String,
) : DialogWrapper(false) {

    private val centerPanel: MarkAsResolvedPanel
    private val changeStatusAction: DialogWrapperAction

    init {
        title = "Mark Issue as Resolved on $productName"
        isResizable = false
        changeStatusAction = object : DialogWrapperAction("Mark as Resolved") {
            init {
                putValue(DEFAULT_ACTION, true)
                isEnabled = false
            }

            override fun doAction(e: ActionEvent) {
                val status = getStatus()

                if(status != null){
                    println("go to server")
                    println("status is:" + status.name)
                }
            }
        }
        centerPanel = MarkAsResolvedPanel(IssueStatus.values().toCollection(ArrayList())) {changeStatusAction.isEnabled = it}
        init()
    }

    private fun getStatus() = centerPanel.selectedStatus

    override fun createCenterPanel() = centerPanel

    override fun createActions() = arrayOf(changeStatusAction, cancelAction)

    override fun getPreferredFocusedComponent() = getButton(cancelAction)
}