/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.trigger

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.common.util.FileUtils.isFileValidForSonarLintWithExtensiveChecks
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.getOpenFiles
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

@Service(Service.Level.PROJECT)
class EditorOpenTrigger(private val myProject: Project) : FileEditorManagerListener {
    fun onProjectOpened() {
        myProject.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)

        runOnPooledThread(myProject) {
            myProject.getOpenFiles().forEach { file ->
                findModuleForFile(file, myProject)?.let { module ->
                    if (SonarLintUtils.isRider()) {
                        getService(BackendService::class.java)
                            .updateFileSystem(mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, file))), true)
                    }

                    getService(BackendService::class.java).didOpenFile(module, file)
                } ?: run {
                    getService(BackendService::class.java).didOpenFile(myProject, file)
                }
            }
        }
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        runOnPooledThread(source.project) {
            val module = findModuleForFile(file, source.project)
            if (module != null && isFileValidForSonarLintWithExtensiveChecks(file, source.project)) {
                getService(BackendService::class.java)
                    .updateFileSystem(mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, file))), true)

                getService(BackendService::class.java).didOpenFile(module, file)
            }
        }
    }
}
