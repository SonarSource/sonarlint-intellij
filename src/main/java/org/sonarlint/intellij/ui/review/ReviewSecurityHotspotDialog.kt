/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.event.ActionEvent
import javax.swing.JButton
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.SECURITY_HOTSPOTS_LINK
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.HOTSPOT_REVIEW_GROUP
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus


class ReviewSecurityHotspotDialog(
    project: Project,
    productName: String,
    module: Module,
    securityHotspotKey: String,
    permissionCheckResponse: CheckStatusChangePermittedResponse,
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

            override fun doAction(e: ActionEvent) {
                val status = getStatus()
                changeStatus(status, ModalityState.stateForComponent(contentPane))
            }

            private fun changeStatus(status: HotspotStatus, modalityState: ModalityState) {
                SonarLintUtils.getService(BackendService::class.java)
                    .changeStatusForHotspot(module, securityHotspotKey, status)
                    .thenAcceptAsync {
                        runOnUiThread(
                            project,
                            modalityState,
                        ) {
                            SonarLintUtils.getService(project, SonarLintToolWindow::class.java)
                                .updateStatusAndApplyCurrentFiltering(securityHotspotKey, status)
                            SonarLintUtils.getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
                            close(OK_EXIT_CODE)
                        }
                    }
                    .exceptionally { error ->
                        SonarLintConsole.get(project).error("Error while changing the Security Hotspot status", error)
                        SonarLintProjectNotifications.get(project).displayErrorNotification("Could not change the Security Hotspot status", HOTSPOT_REVIEW_GROUP)
                        closeDialog(project)
                        null
                    }
            }
        }
        centerPanel = ReviewSecurityHotspotPanel(permissionCheckResponse.allowedStatuses, currentStatus) { changeStatusAction.isEnabled = permissionCheckResponse.isPermitted && it }
        if (!permissionCheckResponse.isPermitted) {
            setErrorText(permissionCheckResponse.notPermittedReason)
        }
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

    private fun closeDialog(project: Project) {
        runOnUiThread(
            project,
            ModalityState.stateForComponent(this@ReviewSecurityHotspotDialog.contentPane)
        ) { close(CANCEL_EXIT_CODE) }
    }

}
