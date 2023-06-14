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
package org.sonarlint.intellij.finding.issue.vulnerabilities

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.actions.RefreshTaintVulnerabilitiesAction
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

sealed class TaintVulnerabilitiesStatus {
  fun isEmpty() = count() == 0
  open fun count() = 0
}

object NoBinding : TaintVulnerabilitiesStatus()

object InvalidBinding : TaintVulnerabilitiesStatus()

data class FoundTaintVulnerabilities(val byFile: Map<VirtualFile, Collection<LocalTaintVulnerability>>) : TaintVulnerabilitiesStatus() {
  override fun count() = byFile.values.stream().mapToInt { it.size }.sum()
}

const val TAINT_VULNERABILITIES_REFRESH_ERROR_MESSAGE = "Error refreshing taint vulnerabilities"

@Service(Service.Level.PROJECT)
class TaintVulnerabilitiesPresenter(private val project: Project) {
  var currentVulnerabilitiesByFile : Map<VirtualFile, Collection<LocalTaintVulnerability>> = emptyMap()

  fun refreshTaintVulnerabilitiesForOpenFilesAsync() {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Refreshing taint vulnerabilities...", false, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        refreshTaintVulnerabilitiesForOpenFiles()
      }
    })
  }

  fun refreshTaintVulnerabilitiesForOpenFiles() {
    try {
      project.getOpenFiles().forEach { refreshTaintVulnerabilitiesFor(project, it) }
    }
    catch (e: Exception) {
      showBalloon(project, TAINT_VULNERABILITIES_REFRESH_ERROR_MESSAGE, RefreshTaintVulnerabilitiesAction("Retry"))
      SonarLintConsole.get(project).error(TAINT_VULNERABILITIES_REFRESH_ERROR_MESSAGE, e)
    }
    presentTaintVulnerabilitiesForOpenFiles()
  }

  private fun refreshTaintVulnerabilitiesFor(project: Project, file: VirtualFile) {
    val bindingManager = getService(project, ProjectBindingManager::class.java)
    val module = project.findModuleOf(file) ?: return
    val projectBinding = getService(module, ModuleBindingManager::class.java).binding ?: return
    val relativePath = SonarLintAppUtils.getRelativePathForAnalysis(project, file) ?: return

    val serverConnection = bindingManager.serverConnection
    val branchName = getService(project, VcsService::class.java).getServerBranchName(module)
    if (branchName != null) {
      bindingManager.connectedEngine.downloadAllServerTaintIssuesForFile(serverConnection.endpointParams,
        getService(BackendService::class.java).getHttpClient(serverConnection.name), projectBinding, relativePath, branchName, null)
    }
  }

  fun presentTaintVulnerabilitiesForOpenFiles() {
    if (project.isDisposed) {
      return
    }
    ProgressManager.getInstance()
      .run(object : Task.Backgroundable(project, "Loading taint vulnerabilities...", false, ALWAYS_BACKGROUND) {
        override fun run(indicator: ProgressIndicator) {
          if (project.isDisposed) {
            return
          }
          val status = TaintVulnerabilitiesLoader.getTaintVulnerabilitiesByOpenedFiles(project)
          currentVulnerabilitiesByFile = if (status is FoundTaintVulnerabilities) status.byFile else emptyMap()
          runOnUiThread(project) {
            getService(project, SonarLintToolWindow::class.java).populateTaintVulnerabilitiesTab(status)
            // annotate the code with intention actions
            if (!status.isEmpty()) {
              getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
            }
          }
        }
      })
  }

  private fun showBalloon(project: Project, message: String, action: AnAction) {
    val notification = GROUP.createNotification(
      "Taint vulnerabilities",
      message,
      NotificationType.ERROR)
    notification.isImportant = true
    notification.addAction(action)
    notification.notify(project)
  }

    companion object {
        val GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Taint vulnerabilities")
    }

}
