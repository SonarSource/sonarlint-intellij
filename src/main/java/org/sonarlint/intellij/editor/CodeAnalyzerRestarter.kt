/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.editor

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import org.sonarlint.intellij.common.util.SonarLintUtils.getService

/**
 * Refreshes the SonarQube highlighting of open files.
 *
 * Highlights are rendered by [DirectHighlighter] directly in the editor markup. That service owns the serialized,
 * debounced refresh queue; this class remains as the entry point used by existing callers.
 */
@Service(Service.Level.PROJECT)
class CodeAnalyzerRestarter @NonInjectable internal constructor(
    private val myProject: Project,
    private val directHighlighter: DirectHighlighter,
) {
    constructor(project: Project) : this(project, getService(project, DirectHighlighter::class.java))

    fun refreshOpenFiles() {
        refreshFiles(FileEditorManager.getInstance(myProject).openFiles.toList())
    }

    fun refreshFiles(changedFiles: Collection<VirtualFile>) {
        if (changedFiles.isEmpty() || myProject.isDisposed) {
            return
        }
        directHighlighter.updateHighlights(changedFiles)
    }
}
