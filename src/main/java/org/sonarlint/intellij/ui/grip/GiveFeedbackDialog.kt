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
package org.sonarlint.intellij.ui.grip

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.event.ActionEvent
import java.net.URI
import java.util.UUID
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils

class GiveFeedbackDialog(
    project: Project,
    findingId: UUID,
    ruleMessage: String,
) : DialogWrapper(false) {

    private val centerPanel: FeedbackPanel
    private val submitFeedbackAction: DialogWrapperAction

    init {
        title = "Submit Feedback for AI Suggestion"
        isResizable = false
        submitFeedbackAction = object : DialogWrapperAction("Submit Feedback") {
            init {
                putValue(DEFAULT_ACTION, true)
            }

            override fun doAction(e: ActionEvent) {
                val inlayHolder = getService(project, InlayHolder::class.java)
                val serviceUri = URI(getGlobalSettings().gripUrl)
                val serviceAuthToken = getGlobalSettings().gripAuthToken
                val promptId = getGlobalSettings().gripPromptVersion
                inlayHolder.getInlayData(findingId)?.let { inlayData ->
                    inlayData.feedbackGiven = true
                    val status = inlayData.getStatus()
                    if (status != null && inlayData.correlationId != null) {
                        getService(BackendService::class.java).provideFeedback(
                            serviceUri,
                            serviceAuthToken,
                            promptId,
                            inlayData.correlationId,
                            status,
                            getStatus(),
                            getComment()
                        )
                    } else {
                        get(project).simpleNotification(
                            null, "Feedback failed.", NotificationType.ERROR
                        )
                    }
                }
                closeDialog(project)
            }
        }
        centerPanel = FeedbackPanel(ruleMessage)
        init()
    }

    private fun getStatus() = centerPanel.getStatus()
    private fun getComment() = centerPanel.getComment()

    override fun createCenterPanel() = centerPanel

    override fun createActions() = arrayOf(submitFeedbackAction, cancelAction)

    override fun getPreferredFocusedComponent() = getButton(cancelAction)

    private fun closeDialog(project: Project) {
        UiUtils.runOnUiThread(
            project,
            ModalityState.stateForComponent(this@GiveFeedbackDialog.contentPane)
        ) { close(OK_EXIT_CODE) }
    }

}
