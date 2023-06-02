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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY

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

        if (issue == null && vulnerability == null) {
            return displayErrorNotification(project, "The issue could not be found.")
        }

        println("Open in Dialog")
    }

    override fun getPriority() = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int) = AllIcons.Actions.BuildLoadChanges
}