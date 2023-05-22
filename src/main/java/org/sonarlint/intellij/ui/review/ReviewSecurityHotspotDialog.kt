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
package org.sonarlint.intellij.ui.review

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation.SECURITY_HOTSPOTS_LINK
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus
import java.awt.event.ActionEvent
import javax.swing.JButton

class ReviewSecurityHotspotDialog(
    project: Project,
    productName: String,
    listOfAllowedStatus: List<HotspotStatus>,
    module: Module,
    securityHotspotKey: String,
    currentStatus: HotspotStatus,
) : DialogWrapper(false) {

    private val centerPanel: ReviewSecurityHotspotPanel
    private val changeStatusAction: DialogWrapperAction

    init {
        title = "Change Security Hotspot Status on $productName"
        isResizable = false
        changeStatusAction = object : DialogWrapperAction("Change Status") {
            init {
                putValue(DEFAULT_ACTION, true)
                isEnabled = false
            }

            override fun doAction(e: ActionEvent?) {
                val status = getStatus()
                SonarLintUtils.getService(BackendService::class.java)
                    .changeStatusForHotspot(BackendService.moduleId(module), securityHotspotKey, status)
                    .thenAccept {
                        runOnUiThread(
                            project,
                            {
                                SonarLintUtils.getService(project, SonarLintToolWindow::class.java)
                                    .updateStatusAndApplyCurrentFiltering(securityHotspotKey, status)
                                SonarLintUtils.getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
                                close(OK_EXIT_CODE)
                            },
                            ModalityState.stateForComponent(this@ReviewSecurityHotspotDialog.contentPane)
                        )
                    }
                    .exceptionally { error ->
                        SonarLintConsole.get(project).error("Error while changing the security hotspot status", error)

                        val notification = ReviewSecurityHotspotAction.GROUP.createNotification(
                            "<b>SonarLint - Unable to change status</b>",
                            "Could not change the security hotspot status.",
                            NotificationType.ERROR
                        )
                        notification.isImportant = true
                        notification.notify(project)

                        runOnUiThread(
                            project,
                            { close(CANCEL_EXIT_CODE) },
                            ModalityState.stateForComponent(this@ReviewSecurityHotspotDialog.contentPane)
                        )
                        null
                    }
            }
        }
        centerPanel = ReviewSecurityHotspotPanel(listOfAllowedStatus, currentStatus) { changeStatusAction.isEnabled = it }
        init()
    }

    override fun createCenterPanel() = centerPanel

    override fun createActions() = arrayOf(changeStatusAction, cancelAction, helpAction)

    override fun getPreferredFocusedComponent() = getButton(cancelAction)

    override fun doHelpAction() = BrowserUtil.browse(SECURITY_HOTSPOTS_LINK)

    override fun setHelpTooltip(helpButton: JButton) {
        helpButton.toolTipText = "Show help contents"
    }

    private fun getStatus() = centerPanel.selectedStatus

}
