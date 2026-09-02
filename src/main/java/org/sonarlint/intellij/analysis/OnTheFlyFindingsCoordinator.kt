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
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.editor.EditorHighlightRefresh
import org.sonarlint.intellij.ui.currentfile.CurrentFileDisplayedFindingsRefresher

/**
 * Resolves an [EditorHighlightRefresh] into a set of files, refreshes the filtered findings snapshot used by
 * [org.sonarlint.intellij.editor.DirectHighlighter], and triggers editor markup refresh independently of the
 * Current File tool window UI lifecycle.
 *
 * The [OnTheFlyFindingsHolder] owns the unfiltered per-open-file maps. Editor squiggles follow the Current File tab
 * filters via [org.sonarlint.intellij.ui.currentfile.CurrentFileDisplayedFindingsStore]. [SonarLintToolWindow.refreshViews]
 * rebuilds panels only; callers that need markup plus panels use [applyHighlightRefreshAndRefreshPanels].
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
        getService(project, CurrentFileDisplayedFindingsRefresher::class.java)
            .refreshDisplayedFindings(SonarLintUtils.getSelectedFile(project))
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
