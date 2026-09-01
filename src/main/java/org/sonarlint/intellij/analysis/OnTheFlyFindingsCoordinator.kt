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
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.editor.EditorHighlightRefresh
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue

/**
 * Publishes on-the-fly analysis findings into editor markup, independently of the Current File tool window.
 *
 * The [OnTheFlyFindingsHolder] owns the per-open-file maps. This service is the highlight publisher that
 * [org.sonarlint.intellij.editor.DirectHighlighter] reads from, and that resolves [EditorHighlightRefresh]
 * into a file set without consulting the Current File tab or its filters.
 */
@Service(Service.Level.PROJECT)
class OnTheFlyFindingsCoordinator(private val project: Project) {

    fun getIssuesForFile(file: VirtualFile): Collection<LiveIssue> =
        holder().getIssuesForFile(file)

    fun getHotspotsForFile(file: VirtualFile): Collection<LiveSecurityHotspot> =
        holder().getSecurityHotspotsForFile(file)

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

    private fun resolveFiles(highlightRefresh: EditorHighlightRefresh): Collection<VirtualFile> {
        return when {
            highlightRefresh.allOpenFiles -> openEditors()
            highlightRefresh.changedFiles != null -> highlightRefresh.changedFiles
            else -> openEditors()
        }
    }

    private fun openEditors(): List<VirtualFile> =
        FileEditorManager.getInstance(project).openFiles.toList()

    private fun holder(): OnTheFlyFindingsHolder =
        getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
}
