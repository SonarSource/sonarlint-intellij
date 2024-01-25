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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.HOTSPOT_REVIEW_GROUP
import org.sonarlint.intellij.tasks.FutureAwaitingTask
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.review.ReviewSecurityHotspotDialog
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedResponse
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus

class ReviewSecurityHotspotAction(private var serverFindingKey: String? = null, private var status: HotspotReviewStatus? = null) :
    AbstractSonarAction(
        "Review Security Hotspot", "Review Security Hotspot Status", null
    ), IntentionAction, PriorityAction, Iconable {

    companion object {
        val SECURITY_HOTSPOT_KEY = DataKey.create<LiveSecurityHotspot>("sonarlint_security_hotspot")
        private const val errorTitle = "<b>SonarLint - Unable to review the Security Hotspot</b>"
        private const val content = "The Security Hotspot status was successfully updated"
    }

    override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus): Boolean {
        return e.getData(SECURITY_HOTSPOT_KEY) != null && e.getData(SECURITY_HOTSPOT_KEY)?.serverFindingKey != null
            && e.getData(SECURITY_HOTSPOT_KEY)?.isValid() == true
    }

    override fun updatePresentation(e: AnActionEvent, project: Project) {
        val serverConnection = serverConnection(project) ?: return
        e.presentation.description = "Review Security Hotspot Status on ${serverConnection.productName}"
    }

    private fun serverConnection(project: Project): ServerConnection? = SonarLintUtils.getService(
        project,
        ProjectBindingManager::class.java
    ).tryGetServerConnection().orElse(null)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val securityHotspot = e.getData(SECURITY_HOTSPOT_KEY)
            ?: return SonarLintProjectNotifications.get(project).displayErrorNotification(errorTitle, "The Security Hotspot could not be found", HOTSPOT_REVIEW_GROUP)
        serverFindingKey = securityHotspot.serverFindingKey
        status = securityHotspot.status

        openReviewingDialogAsync(project, securityHotspot.file())
    }

    fun openReviewingDialogAsync(project: Project, file: VirtualFile) {
        runOnPooledThread(project) {
            openReviewingDialog(project, file)
        }
    }

    private fun openReviewingDialog(project: Project, file: VirtualFile) {
        val connection = serverConnection(project)
            ?: return SonarLintProjectNotifications.get(project).displayErrorNotification(errorTitle, "No connection could be found", HOTSPOT_REVIEW_GROUP)
        val hotspotKey = serverFindingKey
            ?: return SonarLintProjectNotifications.get(project).displayErrorNotification(errorTitle, "Could not find the Security Hotspot on ${connection.productName}", HOTSPOT_REVIEW_GROUP)
        val currentStatus = status
            ?: return SonarLintProjectNotifications.get(project).displayErrorNotification(errorTitle, "Could not find the current Security Hotspot status", HOTSPOT_REVIEW_GROUP)
        val module = ModuleUtil.findModuleForFile(file, project)
            ?: return SonarLintProjectNotifications.get(project).displayErrorNotification(errorTitle, "No module could be found for this file", HOTSPOT_REVIEW_GROUP)

        val response = checkPermission(project, connection, hotspotKey) ?: return
        val newStatus = HotspotStatus.valueOf(currentStatus.name)
        runOnUiThread(project) {
            if (ReviewSecurityHotspotDialog(project, connection.productName, module, hotspotKey, response, newStatus).showAndGet()) {
                SonarLintProjectNotifications.get(project).displaySuccessfulNotification(content, HOTSPOT_REVIEW_GROUP)
            }
        }
    }

    private fun checkPermission(project: Project, connection: ServerConnection, hotspotKey: String): CheckStatusChangePermittedResponse? {
        val checkTask = CheckHotspotStatusChangePermission(project, connection, hotspotKey)
        return try {
            ProgressManager.getInstance().run(checkTask)
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Error while retrieving the list of allowed statuses for Security Hotspots", e)
            SonarLintProjectNotifications.get(project).displayErrorNotification(errorTitle, "Could not check status change permission", HOTSPOT_REVIEW_GROUP)
            null
        }
    }

    override fun startInWriteAction() = false

    override fun getText() = "SonarLint: Change Security Hotspot status"

    override fun getFamilyName() = "SonarLint review"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = serverFindingKey != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file?.let {
            openReviewingDialogAsync(project, it.virtualFile)
        }
    }

    override fun getPriority() = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int) = null

    private class CheckHotspotStatusChangePermission(
            project: Project,
            connection: ServerConnection,
            hotspotKey: String,
    ) :
            FutureAwaitingTask<CheckStatusChangePermittedResponse>(project,
                    "Checking Status Change Permission",
                    SonarLintUtils.getService(BackendService::class.java).checkStatusChangePermitted(connection.name, hotspotKey))

}
