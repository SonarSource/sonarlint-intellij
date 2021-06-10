/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class EditorFileChangeListener : BulkAwareDocumentListener.Simple, StartupActivity {

    private var fileEventsNotifier: ModuleFileEventsNotifier? = null
    private lateinit var project: Project

    @VisibleForTesting
    fun setFileEventsNotifier(fileEventsNotifier: ModuleFileEventsNotifier) {
        this.fileEventsNotifier = fileEventsNotifier
    }

    override fun runActivity(project: Project) {
        this.project = project
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, project)
        if (fileEventsNotifier == null) {
            setFileEventsNotifier(getService(ModuleFileEventsNotifier::class.java))
        }
    }

    override fun afterDocumentChange(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val module = SonarLintAppUtils.findModuleForFile(file, project) ?: return
        val fileEvent = buildModuleFileEvent(module, file, document, ModuleFileEvent.Type.MODIFIED) ?: return
        val engine = getService(project, ProjectBindingManager::class.java).engineIfStarted ?: return
        fileEventsNotifier?.notifyAsync(engine, module, listOf(fileEvent))
    }
}
