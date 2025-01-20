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
package org.sonarlint.intellij.fs

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import java.time.Duration
import org.sonarlint.intellij.common.util.FileUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.Alarm
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

const val DEBOUNCE_DELAY_MS = 1000L

@Service(Service.Level.APP)
class EditorFileChangeListener : BulkAwareDocumentListener.Simple, Disposable {
    private val triggerAlarm = Alarm("sonarlint-editor-changes-notifier", Duration.ofMillis(DEBOUNCE_DELAY_MS)) { notifyPendingChanges() }
    private val changedFiles = LinkedHashSet<VirtualFile>()

    fun startListening() {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, this)
    }

    override fun afterDocumentChange(document: Document) {
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        synchronized(changedFiles) {
            changedFiles.add(virtualFile)
            triggerAlarm.reset()
        }
    }

    private fun notifyPendingChanges() {
        val changedFiles = synchronized(changedFiles) {
            val list = changedFiles.toList()
            changedFiles.clear()
            list
        }

        groupByProject(changedFiles)
            .forEach { (project, files) -> notifyFileChangesForProject(project, files) }
    }

    private fun groupByProject(files: List<VirtualFile>) =
        files.fold(mutableMapOf<Project, MutableList<VirtualFile>>()) { acc, file ->
            ProjectLocator.getInstance().getProjectsForFile(file)
                .filter { !it.isDisposed }
                .forEach { project -> acc.computeIfAbsent(project) { mutableListOf() }.add(file) }
            acc
        }.toMap()

    private fun notifyFileChangesForProject(project: Project, changedFiles: List<VirtualFile>) {
        val filesToSendPerModule = HashMap<Module, MutableList<VirtualFileEvent>>()

        changedFiles
            .filter { FileUtils.Companion.isFileValidForSonarLintWithExtensiveChecks(it, project) }
            .forEach { file ->
                val module = findModuleForFile(file, project) ?: return@forEach
                filesToSendPerModule.computeIfAbsent(module) { mutableListOf() }.add(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file))
            }

        if (filesToSendPerModule.isNotEmpty()) {
            getService(BackendService::class.java).updateFileSystem(filesToSendPerModule, true)
        }
    }

    override fun dispose() {
        changedFiles.clear()
        triggerAlarm.shutdown()
    }
}
