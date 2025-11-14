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
package org.sonarlint.intellij.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.tasks.FutureAwaitingTask
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.resolve.MarkAsResolvedDialog
import org.sonarlint.intellij.util.DataKeys.Companion.ISSUE_DATA_KEY
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.computeOnPooledThread
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.client.utils.IssueResolutionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse

private const val SKIP_CONFIRM_DIALOG_PROPERTY = "SonarLint.markIssueAsResolved.hideConfirmation"

class MarkAsResolvedAction(
    private var issue: Issue? = null,
) :
    AbstractSonarAction(
        "Mark Issue as\u2026", "Change the issue resolution status", null
    ), IntentionAction, PriorityAction, Iconable {

    companion object {
        const val REVIEW_ISSUE_GROUP = "SonarQube for IDE: Mark Issue as Resolved"
        private const val ERROR_TITLE = "<b>SonarQube for IDE - Unable to mark the issue as resolved</b>"
        private const val CONTENT = "The issue was successfully marked as resolved"

        fun canBeMarkedAsResolved(project: Project, issue: Issue): Boolean {
            if (issue.isResolved()) {
                return false
            }
            val serverConnection = serverConnection(project)
            return issue.isValid() && serverConnection != null && (serverConnection.isSonarQube || issue.getServerKey() != null)
        }

        fun openMarkAsResolvedDialogAsync(project: Project, issue: Issue) {
            runOnPooledThread(project) {
                openMarkAsResolvedDialog(project, issue)
            }
        }

        private fun openMarkAsResolvedDialog(project: Project, issue: Issue) {
            val connection = serverConnection(project) ?: return displayNotificationError(project, "No connection could be found")
            val file = issue.file() ?: return displayNotificationError(project, "The file could not be found")
            val module = findModuleForFile(file, project) ?: return displayNotificationError(
                project, "No module could be found for this file"
            )
            val serverKey = issue.getServerKey() ?: issue.getId().toString()
            val response = computeOnPooledThread(project, "Checking permission to mark issue as resolved") {
                checkPermission(project, connection, serverKey)
            } ?: return

            if (response.isPermitted) {
                runOnUiThread(project) {
                    val resolution = MarkAsResolvedDialog(
                        project,
                        connection,
                        response,
                    ).chooseResolution() ?: return@runOnUiThread
                    if (confirm(project, connection.productName, resolution.newStatus)) {
                        runOnPooledThread(project) {
                            markAsResolved(module, issue, resolution, serverKey)
                        }
                    }
                }
            } else {
                SonarLintConsole.get(project).info("Could not resolve the issue, reason: ${response.notPermittedReason}")
                SonarLintProjectNotifications.get(project)
                    .displayErrorNotification(
                        "Could not resolve the issue, reason: ${response.notPermittedReason}",
                        NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                    )
            }
        }

        private fun displayNotificationError(project: Project, content: String) {
            return SonarLintProjectNotifications.get(project).displayErrorNotification(
                ERROR_TITLE,
                content,
                NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
            )
        }

        private fun markAsResolved(
            module: Module,
            issue: Issue,
            resolution: MarkAsResolvedDialog.Resolution,
            issueKey: String,
        ) {
            getService(BackendService::class.java).markAsResolved(module, issueKey, resolution.newStatus, issue is LocalTaintVulnerability)
                .thenAcceptAsync {
                    updateUI(module.project, issue)
                    val comment = resolution.comment ?: return@thenAcceptAsync SonarLintProjectNotifications.get(module.project)
                        .displaySuccessfulNotification(
                            CONTENT,
                            NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                        )
                    addComment(module.project, module, issueKey, comment)
                }.exceptionally { error ->
                    SonarLintConsole.get(module.project).error("Error while marking the issue as resolved", error)
                    SonarLintProjectNotifications.get(module.project)
                        .displayErrorNotification(
                            "Could not mark the issue as resolved",
                            NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                        )
                    null
                }
        }

        private fun updateUI(project: Project, issue: Issue) {
            runOnUiThread(project) {
                issue.resolve()
                getService(project, SonarLintToolWindow::class.java).refreshViews()
                getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
            }
        }

        private fun addComment(project: Project, module: Module, issueKey: String, comment: String) {
            getService(BackendService::class.java).addCommentOnIssue(module, issueKey, comment)
                .thenAccept {
                    SonarLintProjectNotifications.get(project).displaySuccessfulNotification(
                        CONTENT,
                        NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                    )
                }
                .exceptionally { error ->
                    SonarLintConsole.get(project).error("Error while adding a comment on the issue", error)
                    SonarLintProjectNotifications.get(project).displayWarningNotification(
                        "The issue was marked as resolved but there was an error adding the comment",
                        NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                    )
                    null
                }
        }

        private fun checkPermission(project: Project, connection: ServerConnection, issueKey: String): CheckStatusChangePermittedResponse? {
            val checkTask = CheckIssueStatusChangePermission(project, connection, issueKey)
            return try {
                ProgressManager.getInstance().run(checkTask)
            } catch (e: Exception) {
                SonarLintConsole.get(project).error("Error while retrieving the list of allowed statuses for issues", e)
                SonarLintProjectNotifications.get(project)
                    .displayErrorNotification(
                        "Could not check status change permission",
                        NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                    )
                null
            }
        }

        private fun confirm(project: Project, productName: String, issueStatus: IssueResolutionStatus): Boolean {
            return shouldSkipConfirmationDialog() || MessageDialogBuilder.okCancel(
                "Confirm marking issue as resolved",
                "Are you sure you want to mark this issue as \"${issueStatus.title}\"? The status will be updated on $productName and synchronized with any contributor using SonarQube for IDE in Connected Mode"
            ).yesText("Confirm").noText("Cancel").doNotAsk(DoNotShowAgain()).ask(project)
        }

        private fun shouldSkipConfirmationDialog() = PropertiesComponent.getInstance().getBoolean(SKIP_CONFIRM_DIALOG_PROPERTY, false)

        private fun serverConnection(project: Project): ServerConnection? = getService(
            project, ProjectBindingManager::class.java
        ).tryGetServerConnection().orElse(null)
    }

    override fun updatePresentation(e: AnActionEvent, project: Project) {
        val serverConnection = serverConnection(project) ?: return
        e.presentation.description = "Mark Issue as Resolved on ${serverConnection.productName}"
    }


    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        var issue: Issue? = e.getData(ISSUE_DATA_KEY)
        if (issue == null) {
            issue = e.getData(TAINT_VULNERABILITY_DATA_KEY)
        }
        if (issue == null) {
            return SonarLintProjectNotifications.get(project)
                .displayErrorNotification(
                    ERROR_TITLE,
                    "The issue could not be found",
                    NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                )
        }

        openMarkAsResolvedDialogAsync(project, issue)
    }


    private class DoNotShowAgain : DoNotAskOption {
        override fun isToBeShown() = true

        override fun setToBeShown(toBeShown: Boolean, exitCode: Int) {
            PropertiesComponent.getInstance().setValue(SKIP_CONFIRM_DIALOG_PROPERTY, java.lang.Boolean.toString(!toBeShown))
        }

        override fun canBeHidden() = true

        override fun shouldSaveOptionsOnCancel() = false

        override fun getDoNotShowMessage() = "Don't show again"
    }

    override fun getPriority() = PriorityAction.Priority.LOW

    override fun getIcon(flags: Int) = null

    override fun startInWriteAction() = false

    override fun getText() = "SonarQube: Mark issue as\u2026"

    override fun getFamilyName(): String {
        return "SonarQube mark issue as\u2026"
    }

    override fun isVisible(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val issue = e.getData(ISSUE_DATA_KEY) ?: e.getData(TAINT_VULNERABILITY_DATA_KEY) ?: return false
        return canBeMarkedAsResolved(project, issue)
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return issue?.let { canBeMarkedAsResolved(project, it) } ?: false
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file?.let {
            issue?.let { openMarkAsResolvedDialogAsync(project, it) }
        }
    }

    private class CheckIssueStatusChangePermission(
        project: Project,
        connection: ServerConnection,
        issueKey: String,
    ) :
        FutureAwaitingTask<CheckStatusChangePermittedResponse>(
            project,
            "Checking permission to mark issue as resolved",
            getService(BackendService::class.java).checkIssueStatusChangePermitted(connection.name, issueKey)
        )
}
