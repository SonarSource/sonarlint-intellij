/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import org.sonarlint.intellij.editor.actions.CopyCodeExampleAction
import org.sonarlint.intellij.editor.actions.DiffCodeExamplesAction
import org.sonarlint.intellij.ui.grip.CodeSnippet
import org.sonarlint.intellij.ui.grip.InsertAtCursorAction
import org.sonarlint.intellij.ui.ruledescription.RuleCodeSnippet
import org.sonarlint.intellij.ui.traffic.light.SonarLintTrafficLightAction

class SonarLintEditorWidgetActionProvider : InspectionWidgetActionProvider {
    override fun createAction(editor: Editor): AnAction? {
        if (editor.editorKind == EditorKind.UNTYPED && editor.document.getUserData(RuleCodeSnippet.IS_SONARLINT_DOCUMENT) == true) {
            return DefaultActionGroup(DiffCodeExamplesAction(editor), CopyCodeExampleAction(editor))
        }
        if (editor.editorKind == EditorKind.UNTYPED && editor.document.getUserData(CodeSnippet.IS_SONARLINT_AI_DOCUMENT) == true) {
            return DefaultActionGroup(InsertAtCursorAction(editor), CopyCodeExampleAction(editor))
        }
        if (editor.editorKind == EditorKind.MAIN_EDITOR) {
            return DefaultActionGroup(SonarLintTrafficLightAction(editor), Separator.create())
        }
        return null
    }
}
