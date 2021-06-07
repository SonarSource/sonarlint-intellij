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

import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class EditorFileChangeListener(
    private val project: Project,
    private val fileEventsNotifier: ModuleFileEventsNotifier = getService(
        ModuleFileEventsNotifier::class.java
    )
) : BulkAwareDocumentListener {
    override fun documentChanged(event: DocumentEvent) {
        val engine = getService(project, ProjectBindingManager::class.java).engineIfStarted ?: return
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        val module = SonarLintAppUtils.findModuleForFile(file, project) ?: return
        val fileEvent = buildModuleFileEvent(module, file, ModuleFileEvent.Type.MODIFIED) ?: return
        fileEventsNotifier.notifyAsync(engine, module, listOf(fileEvent))
    }
}
