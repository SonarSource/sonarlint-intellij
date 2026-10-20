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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.currentfile.FindingsPanel
import org.sonarlint.intellij.util.SonarLintActions

class FindingsTabToolbarPage internal constructor(
    private val project: Project,
    private val panel: FindingsPanel,
) {

    val isVisible: Boolean
        get() = panel.walkComponents()
            .any { it.javaClass.simpleName.contains("ActionToolbar", ignoreCase = true) }

    val isAnalyzeCurrentFileEnabled: Boolean
        get() = readOnEdt {
            syncFindingsPanelWithSelectedEditor(project, panel)
            isAnalyzeCurrentFileActionEnabled()
        }

    fun analyzeCurrentFile() {
        readOnEdt {
            syncFindingsPanelWithSelectedEditor(project, panel)
            val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                ?: error("No file selected in editor")
            val analyzeAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(file))
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .build()
            val event = AnActionEvent(
                null,
                dataContext,
                ActionPlaces.TOOLBAR,
                Presentation(),
                ActionManager.getInstance(),
                0,
            )
            analyzeAction.actionPerformed(event)
        }
    }

    private fun isAnalyzeCurrentFileActionEnabled(): Boolean {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return false
        val analyzeAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
        val presentation = Presentation()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(file))
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()
        val event = AnActionEvent(
            null,
            dataContext,
            ActionPlaces.TOOLBAR,
            presentation,
            ActionManager.getInstance(),
            0,
        )
        analyzeAction.update(event)
        return presentation.isEnabled && presentation.isVisible
    }

    private fun analyzeCurrentFileButton(): ActionButton? {
        val analyzeAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
        return panel.findComponents<ActionButton>().firstOrNull { it.action === analyzeAction }
    }
}
