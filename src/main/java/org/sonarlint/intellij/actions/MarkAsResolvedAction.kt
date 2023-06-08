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
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.tasks.FutureAwaitingTask
import org.sonarlint.intellij.ui.resolve.MarkAsResolvedDialog
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse

class MarkAsResolvedAction :
    AbstractSonarAction(
        "Mark as Resolved", "Mark as Resolved", null
    ), PriorityAction, Iconable {
    companion object {
        val GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Mark as Resolved")

        fun displayErrorNotification(project: Project, content: String) {
            val notification = GROUP.createNotification(
                "<b>SonarLint - Unable to mark the issue as resolved</b>", content, NotificationType.ERROR
            )
            notification.isImportant = true
            notification.notify(project)
        }
    }

    override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus): Boolean {
        return ((e.getData(DisableRuleAction.ISSUE_DATA_KEY) != null && e.getData(DisableRuleAction.ISSUE_DATA_KEY)?.serverFindingKey != null
            && e.getData(DisableRuleAction.ISSUE_DATA_KEY)?.isValid == true) || (e.getData(TAINT_VULNERABILITY_DATA_KEY) != null))
    }

    override fun updatePresentation(e: AnActionEvent, project: Project) {
        val serverConnection = serverConnection(project) ?: return
        e.presentation.description = "Mark as Resolved on ${serverConnection.productName}"
    }

    private fun serverConnection(project: Project): ServerConnection? = SonarLintUtils.getService(
        project,
        ProjectBindingManager::class.java
    ).tryGetServerConnection().orElse(null)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val issue = e.getData(DisableRuleAction.ISSUE_DATA_KEY)
        val vulnerability = e.getData(TAINT_VULNERABILITY_DATA_KEY)
        val file: VirtualFile
        var isTaintVulnerability: Boolean = false;
        var issueKey: String

        if (issue == null && vulnerability == null) {
            return displayErrorNotification(project, "The issue could not be found.")
        }

        if (issue != null) {
            file = issue.file
            issueKey = issue.serverFindingKey ?: return displayErrorNotification(project, "The issue key could not be found.")
        } else {
            file = vulnerability?.file() ?: return displayErrorNotification(project, "The file could not be found.")
            isTaintVulnerability = true
            issueKey = vulnerability.key()
        }

        openMarkAsResolvedDialog(project, file, issueKey, isTaintVulnerability, issue, vulnerability)
    }

    fun openMarkAsResolvedDialog(
        project: Project, file: VirtualFile, issueKey: String, isTaintVulnerability: Boolean,
        liveIssue: LiveIssue?, taintVulnerability: LocalTaintVulnerability?,
    ) {
        val connection = serverConnection(project) ?: return MarkAsResolvedAction.displayErrorNotification(
            project,
            "No connection could be found."
        )

        val module = ModuleUtil.findModuleForFile(file, project) ?: return MarkAsResolvedAction.displayErrorNotification(
            project, "No module could be found for this file."
        )

        val response = checkPermission(project, connection, issueKey) ?: return

        MarkAsResolvedDialog(
            project,
            connection.productName,
            module,
            issueKey,
            response,
            isTaintVulnerability,
            liveIssue,
            taintVulnerability
        ).show()
    }

    private fun checkPermission(project: Project, connection: ServerConnection, issueKey: String): CheckStatusChangePermittedResponse? {
        val checkTask = CheckIssueStatusChangePermission(project, connection, issueKey)
        return try {
            ProgressManager.getInstance().run(checkTask)
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Error while retrieving the list of allowed statuses for issues", e)
            displayErrorNotification(project, "Could not check status change permission")
            null
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
            SonarLintUtils.getService(BackendService::class.java).checkIssueStatusChangePermitted(connection.name, issueKey)
        )
}