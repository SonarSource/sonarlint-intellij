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
package org.sonarlint.intellij.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import org.sonarlint.intellij.editor.actions.CopyCodeExampleAction
import org.sonarlint.intellij.editor.actions.DiffCodeExamplesAction
import org.sonarlint.intellij.ui.ruledescription.RuleCodeSnippet
import org.sonarlint.intellij.ui.traffic.light.SonarLintTrafficLightAction

class SonarLintEditorWidgetActionProvider : InspectionWidgetActionProvider {
    override fun createAction(editor: Editor): AnAction? {
        if (editor.editorKind == EditorKind.UNTYPED && editor.document.getUserData(RuleCodeSnippet.IS_SONARLINT_DOCUMENT) == true) {
            return DefaultActionGroup(DiffCodeExamplesAction(editor), CopyCodeExampleAction(editor))
        }
        if (offersTrafficLight(editor)) {
            return DefaultActionGroup(SonarLintTrafficLightAction(editor), Separator.create())
        }
        return null
    }

    private fun offersTrafficLight(editor: Editor): Boolean {
        if (editor.editorKind == EditorKind.MAIN_EDITOR) {
            return true
        }
        // Light UI tests use CodeInsightTestFixture editors (UNTYPED); still exercise EP wiring in unit test mode.
        return ApplicationManager.getApplication().isUnitTestMode &&
            editor.editorKind == EditorKind.UNTYPED &&
            editor.document.getUserData(RuleCodeSnippet.IS_SONARLINT_DOCUMENT) != true
    }
}
