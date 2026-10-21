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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JLabel
import org.sonarlint.intellij.fixtures.readOnEdt
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.editor.SonarLintEditorWidgetActionProvider
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.ui.traffic.light.SonarLintTrafficLightAction
import org.sonarlint.intellij.ui.traffic.light.SonarLintTrafficLightWidget

class EditorTrafficLightPage private constructor(
    private val widget: SonarLintTrafficLightWidget,
) {

    val issueCountLabelText: String?
        get() = readOnEdt { widget.findComponents<JLabel>().single().text }

    companion object {
        fun create(project: Project, editor: Editor, file: VirtualFile): EditorTrafficLightPage =
            readOnEdt { createOnEdt(project, editor, file) }

        fun createForSelectedEditor(project: Project): EditorTrafficLightPage =
            readOnEdt {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val file = fileEditorManager.selectedFiles.firstOrNull()
                    ?: error("No selected file")
                val editor = fileEditorManager.selectedTextEditor
                    ?: (fileEditorManager.getSelectedEditor(file) as? TextEditor)?.editor
                    ?: error("No text editor for selected file")
                createOnEdt(project, editor, file)
            }

        private fun createOnEdt(project: Project, editor: Editor, file: VirtualFile): EditorTrafficLightPage {
            val action = trafficLightActionFromExtension(editor)
            val presentation = Presentation()
            val event = actionEvent(project, editor, file, presentation)
            action.update(event)
            val widget = action.createCustomComponent(presentation, INSPECTION_WIDGET_PLACE) as SonarLintTrafficLightWidget
            action.updateCustomComponent(widget, presentation)
            return EditorTrafficLightPage(widget)
        }

        private fun trafficLightActionFromExtension(editor: Editor): SonarLintTrafficLightAction {
            val provider = InspectionWidgetActionProvider.EP_NAME.extensionList
                .filterIsInstance<SonarLintEditorWidgetActionProvider>()
                .singleOrNull()
                ?: error("SonarLintEditorWidgetActionProvider is not registered")
            val group = provider.createAction(editor) as? DefaultActionGroup
            if (group != null) {
                return group.childActionsOrStubs.filterIsInstance<SonarLintTrafficLightAction>().single()
            }
            check(editor.editorKind != EditorKind.MAIN_EDITOR) {
                "SonarLintEditorWidgetActionProvider returned no action for MAIN_EDITOR"
            }
            // Light sandbox editors are often UNTYPED; production only registers the widget for MAIN_EDITOR.
            return SonarLintTrafficLightAction(editor)
        }

        private fun actionEvent(
            project: Project,
            editor: Editor,
            file: VirtualFile,
            presentation: Presentation,
        ): AnActionEvent {
            val event = mock<AnActionEvent>()
            whenever(event.project).thenReturn(project)
            whenever(event.getData(CommonDataKeys.VIRTUAL_FILE)).thenReturn(file)
            whenever(event.getData(CommonDataKeys.EDITOR)).thenReturn(editor)
            whenever(event.presentation).thenReturn(presentation)
            return event
        }

        private const val INSPECTION_WIDGET_PLACE = "InspectionWidget"
    }
}
