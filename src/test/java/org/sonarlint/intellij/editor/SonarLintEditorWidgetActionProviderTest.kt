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

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.ui.traffic.light.SonarLintTrafficLightAction

class SonarLintEditorWidgetActionProviderTest {

    private val provider = SonarLintEditorWidgetActionProvider()

    @Test
    fun createAction_returns_traffic_light_for_main_editor() {
        val editor = editorWithKind(EditorKind.MAIN_EDITOR)

        val group = provider.createAction(editor) as DefaultActionGroup

        assertThat(group.childActionsOrStubs.filterIsInstance<SonarLintTrafficLightAction>()).hasSize(1)
    }

    @Test
    fun createAction_returns_null_for_untyped_editor_without_sonarlint_document() {
        val editor = editorWithKind(EditorKind.UNTYPED)

        assertThat(provider.createAction(editor)).isNull()
    }

    private fun editorWithKind(kind: EditorKind): Editor {
        val document = mock<Document>()
        return mock {
            whenever(it.editorKind).thenReturn(kind)
            whenever(it.document).thenReturn(document)
        }
    }
}
