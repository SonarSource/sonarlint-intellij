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

import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.util.UUID
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.computeInEDT

@Service(Service.Level.PROJECT)
class InlayHolder(private val project: Project) {

    private val inlayPerIssueUuid = mutableMapOf<UUID, InlayData>()

    fun addInlayData(issueUuid: UUID, inlayData: InlayData) {
        inlayPerIssueUuid[issueUuid] = inlayData
    }

    fun addInlayCodeSnippet(issueUuid: UUID, inlaySnippet: InlaySnippetData) {
        inlayPerIssueUuid[issueUuid]?.inlaySnippets?.add(inlaySnippet)
    }

    fun getInlayData(issueUuid: UUID) = inlayPerIssueUuid[issueUuid]

    fun removeAndDisposeInlayData(issueUuid: UUID) {
        inlayPerIssueUuid[issueUuid]?.inlaySnippets?.forEach {
            it.inlayPanel.dispose()
        }
        inlayPerIssueUuid.remove(issueUuid)
    }

    fun removeAndDisposeInlayCodeSnippets(issueUuid: UUID) {
        val snippets = inlayPerIssueUuid[issueUuid]?.inlaySnippets
        snippets?.forEach {
            it.inlayPanel.dispose()
        }
        snippets?.clear()
    }

    fun removeInlayCodeSnippet(issueUuid: UUID, inlaySnippet: InlaySnippetData) {
        inlayPerIssueUuid[issueUuid]?.inlaySnippets?.remove(inlaySnippet)
    }

    fun updateStatusInlayPanel(status: AiFindingState, issueUuid: UUID, inlayQuickFixPanel: InlayQuickFixPanel) {
        val inlayData = inlayPerIssueUuid[issueUuid]
        inlayData?.inlaySnippets?.filter { it.inlayPanel == inlayQuickFixPanel }?.forEach {
            it.status = status
        }
        inlayData?.inlaySnippets?.all { it.status == AiFindingState.ACCEPTED }?.let {
            if (it) {
                inlayData.status = AiFindingState.ACCEPTED
            }
        }
        inlayData?.inlaySnippets?.all { it.status == AiFindingState.DECLINED }?.let {
            if (it) {
                inlayData.status = AiFindingState.DECLINED
            }
        }
        inlayData?.inlaySnippets?.all { it.status == AiFindingState.LOADED }?.let {
            if (it) {
                inlayData.status = AiFindingState.LOADED
            }
        }
        if (inlayData?.inlaySnippets?.all { it.status == AiFindingState.LOADING } == true) {
            inlayData.status = AiFindingState.LOADING
        }
        val hasAccepted = inlayData?.inlaySnippets?.any { it.status == AiFindingState.ACCEPTED } ?: false
        val hasDeclined = inlayData?.inlaySnippets?.any { it.status == AiFindingState.DECLINED } ?: false
        val allResolved =
            inlayData?.inlaySnippets?.all { it.status == AiFindingState.ACCEPTED || it.status == AiFindingState.DECLINED }
                ?: false
        if (hasAccepted && hasDeclined && allResolved) {
            inlayData?.status = AiFindingState.PARTIAL
        }
        inlayData?.inlaySnippets?.all { it.status == AiFindingState.FAILED }?.let {
            if (it) {
                inlayData.status = AiFindingState.FAILED
            }
        }

        if (inlayData != null && (inlayData.status == AiFindingState.ACCEPTED || inlayData.status == AiFindingState.DECLINED || inlayData.status == AiFindingState.PARTIAL)) {
            runOnUiThread(project) {
                getService(
                    project, SonarLintToolWindow::class.java
                ).tryRefreshAiTab(issueUuid)
            }
            get(project).simpleNotification(
                null,
                "The fix has been resolved, please give a feedback!",
                ERROR,
                object : AnAction("Submit Feedback") {
                    override fun actionPerformed(e: AnActionEvent) {
                        runOnUiThread(project) {
                            GiveFeedbackDialog(project, issueUuid, inlayData.ruleMessage).show()
                        }
                    }
                }
            )
        }
    }

    fun regenerateInlay(inlaySnippet: InlaySnippetData, editor: Editor, ruleMessage: String): InlayQuickFixPanel {
        return ApplicationManager.getApplication().computeInEDT {
            val newSnippet = InlayQuickFixPanel(
                project,
                editor,
                inlaySnippet.inlayPanel.inlayLine,
                inlaySnippet.inlayPanel.issueId,
                inlaySnippet.inlayPanel.file,
                inlaySnippet.inlayPanel.correlationId,
                inlaySnippet.index,
                inlaySnippet.total,
                ruleMessage
            )
            newSnippet.updatePanelWithData(
                inlaySnippet.inlayPanel.psiFile!!,
                inlaySnippet.inlayPanel.newCode!!,
                inlaySnippet.inlayPanel.startLine!!,
                inlaySnippet.inlayPanel.endLine!!,
            )
            updateStatusInlayPanel(AiFindingState.LOADED, newSnippet.issueId, newSnippet)
            newSnippet
        }
    }

}
