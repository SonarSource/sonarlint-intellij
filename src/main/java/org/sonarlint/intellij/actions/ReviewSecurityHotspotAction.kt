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

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.ui.review.ReviewSecurityHotspotDialog

class ReviewSecurityHotspotAction(private var serverFindingKey: String? = null) : AbstractSonarAction(
    "Review Security Hotspot", "Review Security Hotspot Status on SonarQube", AllIcons.Actions.BuildLoadChanges
), IntentionAction, PriorityAction, Iconable {

    companion object {
        val GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Security Hotspot Review")
        val SECURITY_HOTSPOT_KEY = DataKey.create<LiveSecurityHotspot>("sonarlint_security_hotspot")
    }

    override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus): Boolean {
        return e.getData(SECURITY_HOTSPOT_KEY) != null && e.getData(SECURITY_HOTSPOT_KEY)?.serverFindingKey != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val securityHotspot =
            e.getData(SECURITY_HOTSPOT_KEY) ?: return displayErrorNotification(project, "The security hotspot could not be found.")
        serverFindingKey = securityHotspot.serverFindingKey

        openReviewingDialog(project, securityHotspot.file)
    }

    fun openReviewingDialog(project: Project, file: VirtualFile) {
        serverFindingKey ?: return displayErrorNotification(project, "Could not find the security hotspot on SonarQube.")
        val module = ModuleUtil.findModuleForFile(file, project) ?: return displayErrorNotification(
            project, "No module could be found for this file."
        )
        val connection = SonarLintUtils.getService(project, ProjectBindingManager::class.java).tryGetServerConnection()
        if (connection.isEmpty) {
            return displayErrorNotification(project, "No connection to SonarQube could be found.")
        }

        SonarLintUtils.getService(BackendService::class.java).listAllowedStatusesForHotspots(connection.get().name)
            .thenAccept { listAllowedStatusesResponse ->
                val listStatuses = listAllowedStatusesResponse.allowedStatuses
                if (listStatuses.isEmpty()) {
                    displayErrorNotification(project, "The statuses for this security hotspot could not be retrieved.")
                } else {
                    if (ReviewSecurityHotspotDialog(project, listStatuses, module, serverFindingKey!!).showAndGet()) {
                        displaySuccessfulNotification(project)
                        SonarLintUtils.getService(project, SonarLintToolWindow::class.java)
                            .removeSecurityHotspotFromList(serverFindingKey!!)
                    }
                }
            }.exceptionally { error ->
                SonarLintConsole.get(project).error("Error while retrieving the list of allowed statuses for security hotspots", error)
                displayErrorNotification(project, "The statuses for this security hotspot could not be retrieved.")
                null
            }
    }

    private fun displayErrorNotification(project: Project, content: String) {
        val notification = GROUP.createNotification(
            "<b>SonarLint - Unable to review the security hotspot</b>", content, NotificationType.ERROR
        )
        notification.isImportant = true
        notification.notify(project)
    }

    private fun displaySuccessfulNotification(project: Project) {
        val notification = GROUP.createNotification(
            "<b>SonarLint - Security hotspot review</b>",
            "The security hotspot status was successfully updated!",
            NotificationType.INFORMATION
        )
        notification.isImportant = true
        notification.notify(project)
    }

    override fun startInWriteAction() = false

    override fun getText() = "SonarLint: Change security hotspot status"

    override fun getFamilyName() = "SonarLint review"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = serverFindingKey != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file?.let {
            openReviewingDialog(project, it.virtualFile)
        }
    }

    override fun getPriority() = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int) = AllIcons.Actions.BuildLoadChanges

}