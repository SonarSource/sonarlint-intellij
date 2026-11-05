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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.util.SonarLintActions

/** Page object for the Findings tab “Analyze Current File” toolbar control. */
class FindingsTabAnalyzeCurrentFileButtonPage internal constructor(
    private val button: ActionButton?,
    private val toolbar: FindingsTabToolbarPage,
    private val findingsTab: FindingsTabPage,
    private val project: Project,
) {

    val isEnabled: Boolean
        get() = readOnEdt {
            val button = button ?: return@readOnEdt false
            button.isEnabled && button.isVisible
        }

    fun click() {
        readOnEdt {
            check(FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()) {
                "No file selected in the editor before Analyze Current File"
            }
            findingsTab.refreshWithEditorSelection()
            toolbar.requireVisible()
            toolbar.refreshActions()
            val button = checkNotNull(button) {
                "Analyze Current File button not found in Findings toolbar"
            }
            val analyzeAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
            ActionUtil.invokeAction(analyzeAction, button, TOOL_WINDOW_ID, null, null)
            pumpEdt()
        }
    }
}
