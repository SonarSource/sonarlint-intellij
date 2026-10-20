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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import javax.swing.JLabel
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
        get() {
            UIUtil.dispatchAllInvocationEvents()
            return widget.findComponents<JLabel>().single().text
        }

    companion object {
        fun create(project: Project, editor: Editor, file: VirtualFile): EditorTrafficLightPage {
            val action = trafficLightActionFromExtension(editor)
            val presentation = Presentation()
            val event = actionEvent(project, editor, file, presentation)
            action.update(event)
            val widget = action.createCustomComponent(presentation, INSPECTION_WIDGET_PLACE) as SonarLintTrafficLightWidget
            action.updateCustomComponent(widget, presentation)
            UIUtil.dispatchAllInvocationEvents()
            return EditorTrafficLightPage(widget)
        }

        fun createForSelectedEditor(project: Project): EditorTrafficLightPage {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                ?: error("No selected editor")
            val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                ?: error("No selected file")
            return create(project, editor, file)
        }

        private fun trafficLightActionFromExtension(editor: Editor): SonarLintTrafficLightAction {
            val provider = InspectionWidgetActionProvider.EP_NAME.extensionList
                .filterIsInstance<SonarLintEditorWidgetActionProvider>()
                .single()
            val group = provider.createAction(editor) as DefaultActionGroup
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
