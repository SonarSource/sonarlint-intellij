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
package org.sonarlint.intellij.actions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.tasks.FutureAwaitingTask
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.resolve.MarkAsResolvedDialog
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY
import org.sonarlint.intellij.util.displayErrorNotification
import org.sonarlint.intellij.util.displaySuccessfulNotification
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus

private const val SKIP_CONFIRM_DIALOG_PROPERTY = "SonarLint.markIssueAsResolved.hideConfirmation"

class MarkAsResolvedAction :
    AbstractSonarAction(
        "Mark as Resolved", "Mark as Resolved", null
    ), PriorityAction, Iconable {
    companion object {
        const val successTitle = "<b>SonarLint - Mark as Resolved</b>"
        const val errorTitle = "<b>SonarLint - Unable to mark the issue as resolved</b>"
        const val content = "The issue was successfully marked as resolved!"

        val GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Mark as Resolved")

        fun openMarkAsResolvedDialog(
            project: Project, file: VirtualFile, issueKey: String, isTaintVulnerability: Boolean,
            liveIssue: LiveIssue?, taintVulnerability: LocalTaintVulnerability?,
        ) {
            val connection = serverConnection(project) ?: return displayErrorNotification(
                project,
                errorTitle, "No connection could be found.", GROUP
            )

            val module = ModuleUtil.findModuleForFile(file, project) ?: return displayErrorNotification(
                project, errorTitle, "No module could be found for this file.", GROUP
            )

            val response = checkPermission(project, connection, issueKey) ?: return

            val resolution = MarkAsResolvedDialog(
                project,
                connection,
                response,
            ).chooseResolution() ?: return
            if (confirm(project, connection.productName, resolution.newStatus)) {
                markAsResolved(project, module, liveIssue, taintVulnerability, issueKey, resolution, isTaintVulnerability)
            }


        }

        fun markAsResolved(
            project: Project,
            module: Module,
            liveIssue: LiveIssue?,
            localTaintVulnerability: LocalTaintVulnerability?,
            issueKey: String,
            resolution: MarkAsResolvedDialog.Resolution,
            isTaintVulnerability: Boolean,
        ) {
            getService(BackendService::class.java)
                .markAsResolved(module, issueKey, resolution.newStatus, isTaintVulnerability)
                .thenAccept {
                    displaySuccessfulNotification(project, successTitle, content, GROUP)
                    updateUI(project, isTaintVulnerability, localTaintVulnerability, liveIssue)
                    val comment = resolution.comment ?: return@thenAccept
                    addComment(project, module, issueKey, comment)
                }
                .exceptionally { error ->
                    SonarLintConsole.get(project).error("Error while marking issue as resolved", error)

                    notifyError(project, "<b>SonarLint - Unable to mark as resolved</b>", "Could not mark the issue as resolved.")
                    null
                }
        }

        private fun notifyError(project: Project, title: String, content: String) {
            val notification = GROUP.createNotification(
                title,
                content,
                NotificationType.ERROR
            )
            notification.isImportant = true
            notification.notify(project)
        }

        private fun updateUI(
            project: Project,
            isTaintVulnerability: Boolean,
            localTaintVulnerability: LocalTaintVulnerability?,
            liveIssue: LiveIssue?,
        ) {
            runOnUiThread(project) {
                val toolWindowService = getService(project, SonarLintToolWindow::class.java)

                if (isTaintVulnerability) {
                    toolWindowService.markAsResolved(localTaintVulnerability)
                } else {
                    toolWindowService.markAsResolved(liveIssue)
                }
                getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
            }
        }

        private fun addComment(project: Project, module: Module, issueKey: String, comment: String) {
            getService(BackendService::class.java)
                .addCommentOnIssue(module, issueKey, comment)
                .exceptionally { error ->
                    SonarLintConsole.get(project).error("Error while adding a comment on the issue", error)
                    notifyError(project, "<b>SonarLint - Unable to add a comment</b>", "Could not add a comment on the issue.")
                    null
                }
        }

        private fun checkPermission(project: Project, connection: ServerConnection, issueKey: String): CheckStatusChangePermittedResponse? {
            val checkTask = CheckIssueStatusChangePermission(project, connection, issueKey)
            return try {
                ProgressManager.getInstance().run(checkTask)
            } catch (e: Exception) {
                SonarLintConsole.get(project).error("Error while retrieving the list of allowed statuses for issues", e)
                displayErrorNotification(project, errorTitle, "Could not check status change permission", GROUP)
                null
            }
        }

        private fun confirm(project: Project, productName: String, issueStatus: IssueStatus): Boolean {
            if (PropertiesComponent.getInstance().getBoolean(SKIP_CONFIRM_DIALOG_PROPERTY, false)) {
                return true
            }
            return MessageDialogBuilder.okCancel(
                "Confirm marking issue as resolved",
                "Are you sure you want to mark this issue as \"${issueStatus.title}\"? The status will be modified on $productName"
            )
                .yesText("Confirm")
                .noText("Cancel")
                .doNotAsk(DoNotShowAgain())
                .ask(project)
        }

        private fun serverConnection(project: Project): ServerConnection? = getService(
            project,
            ProjectBindingManager::class.java
        ).tryGetServerConnection().orElse(null)
    }

    override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus): Boolean {
        return ((e.getData(DisableRuleAction.ISSUE_DATA_KEY) != null && e.getData(DisableRuleAction.ISSUE_DATA_KEY)?.serverFindingKey != null
            && e.getData(DisableRuleAction.ISSUE_DATA_KEY)?.isValid == true) || (e.getData(TAINT_VULNERABILITY_DATA_KEY) != null))
    }

    override fun updatePresentation(e: AnActionEvent, project: Project) {
        val serverConnection = serverConnection(project) ?: return
        e.presentation.description = "Mark as Resolved on ${serverConnection.productName}"
    }


    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val issue = e.getData(DisableRuleAction.ISSUE_DATA_KEY)
        val vulnerability = e.getData(TAINT_VULNERABILITY_DATA_KEY)
        val file: VirtualFile
        var isTaintVulnerability: Boolean = false;
        var issueKey: String

        if (issue == null && vulnerability == null) {
            return displayErrorNotification(project, errorTitle, "The issue could not be found.", GROUP)
        }

        if (issue != null) {
            file = issue.file
            issueKey =
                issue.serverFindingKey ?: return displayErrorNotification(project, errorTitle, "The issue key could not be found.", GROUP)
        } else {
            file = vulnerability?.file() ?: return displayErrorNotification(project, errorTitle, "The file could not be found.", GROUP)
            isTaintVulnerability = true
            issueKey = vulnerability.key()
        }

        openMarkAsResolvedDialog(project, file, issueKey, isTaintVulnerability, issue, vulnerability)
    }


    private class DoNotShowAgain : DoNotAskOption {
        override fun isToBeShown(): Boolean {
            return true
        }

        override fun setToBeShown(toBeShown: Boolean, exitCode: Int) {
            PropertiesComponent.getInstance().setValue(SKIP_CONFIRM_DIALOG_PROPERTY, java.lang.Boolean.toString(!toBeShown))
        }

        override fun canBeHidden(): Boolean {
            return true
        }

        override fun shouldSaveOptionsOnCancel(): Boolean {
            return false
        }

        override fun getDoNotShowMessage(): String {
            return "Don't show again"
        }
    }


    override fun getPriority() = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int) = AllIcons.Actions.BuildLoadChanges

    private class CheckIssueStatusChangePermission(
        project: Project,
        connection: ServerConnection,
        issueKey: String,
    ) :
        FutureAwaitingTask<CheckStatusChangePermittedResponse>(
            project,
            "Checking Mark as Resolved Permission",
            getService(BackendService::class.java).checkIssueStatusChangePermitted(connection.name, issueKey)
        )
}
