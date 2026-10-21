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
package org.sonarlint.intellij.ui.page

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.EditorHighlightRefresh
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt

/** Refreshes the Findings tab through [SonarLintToolWindow], matching production updates. */
internal fun syncFindingsTabWithEditorSelection(project: Project) {
    readOnEdt {
        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val file = resolveFindingsFile(project, selected)
        if (file != null) {
            getService(project, SonarLintToolWindow::class.java).updateCurrentFileTab(file, EditorHighlightRefresh.NONE)
        }
        repeat(10) { pumpEdt() }
    }
}

/** Uses the holder's [VirtualFile] key (light tests may use a different instance/path than the editor selection). */
private fun resolveFindingsFile(project: Project, selected: VirtualFile?): VirtualFile? {
    val holder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
    val issueFile = holder.getAllIssues().firstOrNull()?.file()
    if (issueFile != null) {
        return issueFile
    }
    return selected
}
