/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.serviceContainer.NonInjectable
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.runReadActionSafely
import org.sonarlint.intellij.util.runOnPooledThread

@Service(Service.Level.PROJECT)
class CodeAnalyzerRestarter @NonInjectable internal constructor(private val myProject: Project, private val codeAnalyzer: DaemonCodeAnalyzer) {
    constructor(project: Project) : this(project, DaemonCodeAnalyzer.getInstance(project))

    fun refreshOpenFiles() {
        refreshFiles(FileEditorManager.getInstance(myProject).openFiles.toList())
    }

    fun refreshFiles(changedFiles: Collection<VirtualFile>) {
        runOnPooledThread(myProject) {
            val fileEditorManager = FileEditorManager.getInstance(myProject)
            val openFiles = fileEditorManager.openFiles
            runReadActionSafely(myProject) {
                openFiles
                    .filter { it in changedFiles }
                    .mapNotNull { getPsi(it) }
                    .forEach { codeAnalyzer.restart(it) }
            }
        }
    }

    private fun getPsi(virtualFile: VirtualFile): PsiFile? {
        if (!virtualFile.isValid) {
            return null
        }
        ApplicationManager.getApplication().assertReadAccessAllowed()
        return PsiManager.getInstance(myProject).findFile(virtualFile)
    }
}
