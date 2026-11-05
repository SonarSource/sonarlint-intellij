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

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.fileEditor.FileEditorManager
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.currentfile.FindingsPanel
import org.sonarlint.intellij.util.SonarLintActions

/** Page object for the Findings tab “Analyze Current File” toolbar control. */
class FindingsTabAnalyzeCurrentFileButtonPage internal constructor(
    private val toolbar: FindingsTabToolbarPage,
    private val findingsTab: FindingsTabPage,
    private val project: Project,
    private val panel: FindingsPanel,
) {

    val isEnabled: Boolean
        get() = readOnEdt {
            toolbar.requireVisible()
            val button = findToolbarButton() ?: return@readOnEdt false
            button.isEnabled && button.isVisible
        }

    fun click() {
        readOnEdt {
            check(FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()) {
                "No file selected in the editor before Analyze Current File"
            }
            syncFindingsTabWithEditor()
            toolbar.requireVisible()
            val analyzeAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
            val button = checkNotNull(findToolbarButton()) {
                "Analyze Current File button not found in Findings toolbar"
            }
            ActionUtil.invokeAction(analyzeAction, button, TOOL_WINDOW_ID, null, null)
            pumpEdt()
        }
    }

    private fun syncFindingsTabWithEditor() {
        findingsTab.refreshWithEditorSelection()
    }

    private fun findToolbarButton(): ActionButton? {
        val analyzeAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
        val toolbarButtons = toolbarActionButtons()
        return toolbarButtons.firstOrNull { it.action === analyzeAction }
            ?: toolbarButtons.firstOrNull { it.action.templateText == analyzeAction.templateText }
    }

    private fun toolbarActionButtons(): List<ActionButton> {
        panel.findingsMainToolbar()?.updateActionsImmediately()
        val roots = buildList {
            panel.findingsMainToolbar()?.component?.let { add(it) }
            if (panel is SimpleToolWindowPanel) {
                panel.getToolbar()?.let { add(it) }
            }
            add(panel)
        }
        return roots.flatMap { root ->
            root.walkComponents()
                .filter { it.javaClass.simpleName.contains("ActionButton") }
                .mapNotNull { it as? ActionButton }
        }
    }
}
