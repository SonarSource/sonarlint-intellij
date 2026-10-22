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
        /** Builds the widget from [InspectionWidgetActionProvider] (requires [com.intellij.openapi.editor.EditorKind.MAIN_EDITOR]). */
        fun create(project: Project, editor: Editor, file: VirtualFile): EditorTrafficLightPage =
            readOnEdt { buildPage(project, editor, file, trafficLightActionFromExtension(editor)) }

        /**
         * Builds the widget from [SonarLintTrafficLightAction] directly.
         * Light-test editors are [com.intellij.openapi.editor.EditorKind.UNTYPED]; use this for light UI tests.
         * EP wiring for [com.intellij.openapi.editor.EditorKind.MAIN_EDITOR] is covered by [org.sonarlint.intellij.editor.SonarLintEditorWidgetActionProviderTest].
         */
        fun createFromTrafficLightAction(project: Project, editor: Editor, file: VirtualFile): EditorTrafficLightPage =
            readOnEdt { buildPage(project, editor, file, SonarLintTrafficLightAction(editor)) }

        fun createForSelectedEditor(project: Project): EditorTrafficLightPage =
            readOnEdt {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val file = fileEditorManager.selectedFiles.firstOrNull()
                    ?: error("No selected file")
                val editor = (fileEditorManager.getSelectedEditor(file) as? TextEditor)?.editor
                    ?: fileEditorManager.selectedTextEditor
                    ?: error("No text editor for selected file")
                create(project, editor, file)
            }

        private fun buildPage(
            project: Project,
            editor: Editor,
            file: VirtualFile,
            action: SonarLintTrafficLightAction,
        ): EditorTrafficLightPage {
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
                ?: error(
                    "SonarLintEditorWidgetActionProvider returned no action for editor kind ${editor.editorKind}. " +
                        "Use createFromTrafficLightAction() in light tests.",
                )
            return group.childActionsOrStubs.filterIsInstance<SonarLintTrafficLightAction>().single()
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
