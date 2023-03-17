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
package org.sonarlint.intellij.finding.hotspot

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.actions.RefreshSecurityHotspotsAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.vcs.VcsService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.findModuleOf
import org.sonarlint.intellij.util.getOpenFiles

sealed class SecurityHotspotsLocalDetectionSupport

data class NotSupported(val reason: String) : SecurityHotspotsLocalDetectionSupport()
object Supported : SecurityHotspotsLocalDetectionSupport()

const val SECURITY_HOTSPOTS_REFRESH_ERROR_MESSAGE = "Error refreshing security hotspots"

class SecurityHotspotsPresenter(private val project: Project) {

    fun presentSecurityHotspotsForOpenFiles() {
        getService(BackendService::class.java)
            .checkLocalSecurityHotspotDetectionSupported(project)
            .thenApply { response -> if (response.isSupported) Supported else NotSupported(response.reason!!) }
            .thenAccept { status ->
                runOnUiThread(project) {
                    getService(project, SonarLintToolWindow::class.java).populateSecurityHotspotsTab(status)
                    if (status is Supported) {
                        getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
                    }
                }
            }
    }

    fun refreshSecurityHotspotsForOpenFilesAsync() {
        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Refreshing security hotspots...", false, ALWAYS_BACKGROUND) {
                override fun run(indicator: ProgressIndicator) {
                    refreshSecurityHotspotsForOpenFiles()
                }
            })
    }

    fun refreshSecurityHotspotsForOpenFiles() {
        try {
            project.getOpenFiles().forEach { refreshSecurityHotspotsFor(project, it) }
        } catch (e: Exception) {
            showBalloon(project, SECURITY_HOTSPOTS_REFRESH_ERROR_MESSAGE, RefreshSecurityHotspotsAction("Retry"))
            SonarLintConsole.get(project).error(SECURITY_HOTSPOTS_REFRESH_ERROR_MESSAGE, e)
        }
        presentSecurityHotspotsForOpenFiles()
    }

    private fun refreshSecurityHotspotsFor(project: Project, file: VirtualFile) {
        val bindingManager = getService(project, ProjectBindingManager::class.java)
        val module = project.findModuleOf(file) ?: return
        val projectBinding = getService(module, ModuleBindingManager::class.java).binding ?: return
        val relativePath = SonarLintAppUtils.getRelativePathForAnalysis(project, file) ?: return

        val serverConnection = bindingManager.serverConnection
        val branchName = getService(project, VcsService::class.java).getServerBranchName(module)
        if (branchName != null) {
            bindingManager.connectedEngine.downloadAllServerHotspotsForFile(
                serverConnection.endpointParams,
                serverConnection.httpClient, projectBinding, relativePath, branchName, null
            )
        }
    }

    private fun showBalloon(project: Project, message: String, action: AnAction) {
        val notification = GROUP.createNotification(
            "Security hotspots",
            message,
            NotificationType.ERROR
        )
        notification.isImportant = true
        notification.addAction(action)
        notification.notify(project)
    }

    companion object {
        val GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Security hotspots")
    }
}
