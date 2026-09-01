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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.editor.EditorHighlightRefresh

/**
 * Resolves an [EditorHighlightRefresh] into a set of files and triggers the editor markup refresh,
 * independently of the Current File tool window.
 *
 * The [OnTheFlyFindingsHolder] owns the per-open-file maps and is read directly by
 * [org.sonarlint.intellij.editor.DirectHighlighter]. This service never consults the Current File
 * tab or its filters, and [SonarLintToolWindow.refreshViews] no longer refreshes markup on its own:
 * callers that need both must use [applyHighlightRefreshAndRefreshPanels].
 */
@Service(Service.Level.PROJECT)
class OnTheFlyFindingsCoordinator(private val project: Project) {

    fun applyHighlightRefresh(highlightRefresh: EditorHighlightRefresh) {
        if (!highlightRefresh.enabled || project.isDisposed) {
            return
        }
        val files = resolveFiles(highlightRefresh).filter { it.isValid }
        if (files.isEmpty()) {
            return
        }
        getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(files)
    }

    /**
     * Refreshes editor markup, then rebuilds tool-window panels. Use when findings caches changed outside the
     * holder's analysis publish path (taints, CAYC, resolve actions, binding changes).
     */
    fun applyHighlightRefreshAndRefreshPanels(highlightRefresh: EditorHighlightRefresh) {
        applyHighlightRefresh(highlightRefresh)
        if (!project.isDisposed) {
            getService(project, SonarLintToolWindow::class.java).refreshViews()
        }
    }

    private fun resolveFiles(highlightRefresh: EditorHighlightRefresh): Collection<VirtualFile> {
        return when {
            highlightRefresh.allOpenFiles -> openEditors()
            highlightRefresh.changedFiles != null -> highlightRefresh.changedFiles
            else -> openEditors()
        }
    }

    private fun openEditors(): List<VirtualFile> =
        FileEditorManager.getInstance(project).openFiles.toList()
}
