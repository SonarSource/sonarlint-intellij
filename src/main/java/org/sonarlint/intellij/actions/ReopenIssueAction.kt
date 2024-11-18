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
package org.sonarlint.intellij.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.actions.MarkAsResolvedAction.Companion.REVIEW_ISSUE_GROUP
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.UiUtils
import org.sonarlint.intellij.util.DataKeys
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.runOnPooledThread

private const val SKIP_CONFIRM_REOPEN_DIALOG_PROPERTY = "SonarLint.reopenIssue.hideConfirmation"

class ReopenIssueAction(private var issue: LiveIssue? = null) : AbstractSonarAction("Reopen", "Reopen the issue", null), IntentionAction,
    PriorityAction, Iconable {

    companion object {
        private const val ERROR_TITLE = "<b>SonarQube for IntelliJ - Unable to reopen the issue</b>"
        private const val CONTENT = "The issue was successfully reopened"

        fun canBeReopened(project: Project, issue: Issue): Boolean {
            return serverConnection(project) != null && issue.isResolved()
        }

        fun reopenIssueDialog(project: Project, issue: Issue) {
            val connection = serverConnection(project) ?: return displayNotificationError(project, "No connection could be found")
            val file = issue.file() ?: return displayNotificationError(project, "The file could not be found")
            val module = findModuleForFile(file, project) ?: return displayNotificationError(
                project, "No module could be found for this file"
            )

            var serverKey: String? = null
            if (issue is LiveIssue) {
                serverKey = issue.getServerKey() ?: issue.getId().toString()
            } else if (issue is LocalTaintVulnerability) {
                serverKey = issue.key()
            }
            serverKey ?: return displayNotificationError(project, "The issue key could not be found")

            if (confirm(project, connection.productName)) {
                runOnPooledThread(project) { reopenFinding(project, module, issue, serverKey) }
            }
        }

        private fun displayNotificationError(project: Project, content: String) {
            return SonarLintProjectNotifications.get(project).displayErrorNotification(
                ERROR_TITLE, content, NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
            )
        }

        private fun reopenFinding(project: Project, module: Module, issue: Issue, issueKey: String) {
            SonarLintUtils.getService(BackendService::class.java).reopenIssue(module, issueKey, issue is LocalTaintVulnerability)
                .thenAcceptAsync {
                    updateUI(project, issue)
                    SonarLintProjectNotifications.get(project).displaySuccessfulNotification(
                        CONTENT, NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                    )
                }.exceptionally { error ->
                    SonarLintConsole.get(project).error("Error while reopening the issue", error)
                    SonarLintProjectNotifications.get(project).displayErrorNotification(
                        "Could not reopen the issue",
                        NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
                    )
                    null
                }
        }

        private fun updateUI(project: Project, issue: Issue) {
            UiUtils.runOnUiThread(project) {
                issue.reopen()
                SonarLintUtils.getService(project, SonarLintToolWindow::class.java).reopenIssue(issue)
                SonarLintUtils.getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
            }
        }

        private fun confirm(project: Project, productName: String): Boolean {
            return shouldSkipConfirmationDialogForReopening() || MessageDialogBuilder.okCancel(
                "Confirm reopening the issue",
                "Are you sure you want to reopen this issue? The status will be updated on $productName and synchronized with any contributor using SonarQube for IntelliJ in Connected Mode"
            ).yesText("Confirm").noText("Cancel").doNotAsk(DoNotShowAgain()).ask(project)
        }

        private fun shouldSkipConfirmationDialogForReopening() =
            PropertiesComponent.getInstance().getBoolean(SKIP_CONFIRM_REOPEN_DIALOG_PROPERTY, false)

        private fun serverConnection(project: Project): ServerConnection? = SonarLintUtils.getService(
            project, ProjectBindingManager::class.java
        ).tryGetServerConnection().orElse(null)
    }

    override fun updatePresentation(e: AnActionEvent, project: Project) {
        e.presentation.description = "Reopen the issue"
    }


    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val issue = e.getData(DataKeys.ISSUE_DATA_KEY) ?: e.getData(DataKeys.TAINT_VULNERABILITY_DATA_KEY)
        ?: return SonarLintProjectNotifications.get(project).displayErrorNotification(
            ERROR_TITLE,
            "The issue could not be found",
            NotificationGroupManager.getInstance().getNotificationGroup(REVIEW_ISSUE_GROUP)
        )

        reopenIssueDialog(project, issue)
    }


    private class DoNotShowAgain : DoNotAskOption {
        override fun isToBeShown() = true

        override fun setToBeShown(toBeShown: Boolean, exitCode: Int) {
            PropertiesComponent.getInstance().setValue(SKIP_CONFIRM_REOPEN_DIALOG_PROPERTY, java.lang.Boolean.toString(!toBeShown))
        }

        override fun canBeHidden() = true

        override fun shouldSaveOptionsOnCancel() = false

        override fun getDoNotShowMessage() = "Don't show again"
    }

    override fun getPriority() = PriorityAction.Priority.LOW

    override fun getIcon(flags: Int) = null

    override fun startInWriteAction() = false

    override fun getText() = "SonarQube: Reopen issue"

    override fun getFamilyName(): String {
        return "SonarQube reopen issue"
    }

    override fun isVisible(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val issue = e.getData(DataKeys.ISSUE_DATA_KEY) ?: e.getData(DataKeys.TAINT_VULNERABILITY_DATA_KEY) ?: return false
        return canBeReopened(project, issue)
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return issue?.let { canBeReopened(project, it) } == true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file?.let {
            issue?.let { reopenIssueDialog(project, it) }
        }
    }
}
