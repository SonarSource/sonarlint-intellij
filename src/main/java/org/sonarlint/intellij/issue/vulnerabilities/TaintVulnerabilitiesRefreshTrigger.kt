/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.issue.vulnerabilities

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.vcs.VcsListener
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.messages.ProjectConfigurationListener
import org.sonarlint.intellij.tasks.BindingStorageUpdateTask
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.getOpenFiles

class TaintVulnerabilitiesRefreshTrigger(private val project: Project) {

  fun subscribeToTriggeringEvents() {
    val busConnection = project.messageBus.connect()
    with(busConnection) {
      subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          triggerRefresh()
        }

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          triggerRefresh()
        }
      })
      subscribe(ProjectConfigurationListener.TOPIC, ProjectConfigurationListener { triggerRefresh() })
      subscribe(GlobalConfigurationListener.TOPIC, object : GlobalConfigurationListener.Adapter() {
        override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
          triggerRefresh()
        }
      })
      subscribe(BindingStorageUpdateTask.Listener.TOPIC, BindingStorageUpdateTask.Listener { runInEdt { triggerRefresh() } })
      subscribe(VcsListener.TOPIC, VcsListener { module, _ ->
        if (project.getOpenFiles().any { module == findModuleForFile(it, project) }) {
          ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Refreshing taint vulnerabilities...", false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
              getService(project, TaintVulnerabilitiesPresenter::class.java).refreshTaintVulnerabilitiesForOpenFiles(project)
            }
          })
        }})
    }

    triggerRefresh()
  }

  private fun triggerRefresh() {
    getService(project, TaintVulnerabilitiesPresenter::class.java).presentTaintVulnerabilitiesForOpenFiles()
  }
}
