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
import org.sonarlint.intellij.actions.SonarLintToolWindow
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.editor.EditorHighlightRefresh
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.currentfile.FindingsPanel
import org.sonarlint.intellij.ui.currentfile.TreeType
import org.sonarlint.intellij.ui.tree.IssueTree

class FindingsTabPage internal constructor(
    private val project: Project,
    private val panel: FindingsPanel,
) {

    val toolbar: FindingsTabToolbarPage = FindingsTabToolbarPage(this, project, panel)

    val filters: FindingsTabFiltersPanelPage
        get() = readPanelState { FindingsTabFiltersPanelPage(project, panel) }

    val findingsTree: FindingsTreePage
        get() = readPanelState { FindingsTreePage(findingsIssueTree()) }

    val oldFindingsTree: FindingsTreePage
        get() = readPanelState { FindingsTreePage(oldFindingsIssueTree()) }

    val issues: List<FindingNodePage>
        get() = readPanelState {
            FindingsTreePage(findingsIssueTree()).issues + FindingsTreePage(oldFindingsIssueTree()).issues
        }

    /** Text of the issues row in [org.sonarlint.intellij.ui.currentfile.CurrentFileSummaryPanel]. */
    val issuesSummaryButtonText: String
        get() = readPanelState { readIssuesSummaryButtonText() }

    /** Empty-state banner in the issues area (e.g. "No findings to display"). */
    val emptyStateText: String
        get() = readPanelState {
            panel.walkComponents()
                .filterIsInstance<JBPanelWithEmptyText>()
                .first()
                .emptyText.text
        }

    /** Refreshes the tab through [SonarLintToolWindow]; call only on the EDT. */
    internal fun refreshWithEditorSelection() {
        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selected != null) {
            getService(project, SonarLintToolWindow::class.java).updateCurrentFileTab(selected, EditorHighlightRefresh.NONE)
        }
        repeat(10) { pumpEdt() }
    }

    private fun <T> readPanelState(read: () -> T): T =
        readOnEdt {
            repeat(30) { pumpEdt() }
            read()
        }

    private fun readIssuesSummaryButtonText(): String =
        panel.walkComponents()
            .filterIsInstance<JBLabel>()
            .map { it.text }
            .first { text -> text == "1 Issue" || text == "No Issues" || text.matches(ISSUES_SUMMARY_COUNT) }

    private fun findingsIssueTree(): IssueTree =
        panel.getTree(TreeType.ISSUES, isOld = false) as IssueTree

    private fun oldFindingsIssueTree(): IssueTree =
        panel.getTree(TreeType.ISSUES, isOld = true) as IssueTree

    private companion object {
        private val ISSUES_SUMMARY_COUNT = Regex("\\d+ Issues")
    }
}
