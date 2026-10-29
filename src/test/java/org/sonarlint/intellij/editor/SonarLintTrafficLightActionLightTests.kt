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

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.page.EditorTrafficLightPage

class SonarLintTrafficLightActionLightTests : AbstractSonarLintUILightTests() {

    @BeforeEach
    fun waitForReady() {
        waitUntilAnalysisReady()
    }

    @Test
    fun should_show_issue_count_on_traffic_light_after_analysis() {
        val file = openFileFromTestData("src/file.xml")
        val editor = readOnEdt {
            val fileEditorManager = FileEditorManager.getInstance(project)
            (fileEditorManager.getSelectedEditor(file) as? TextEditor)?.editor
                ?: fileEditorManager.selectedTextEditor
                ?: myFixture.editor
        }

        mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click(file)

        awaitUntilAsserted {
            assertThat(mainToolWindow.findings.issues).hasSize(1)
            assertThat(
                EditorTrafficLightPage.createFromTrafficLightAction(project, editor, file).issueCountLabelText,
            ).isEqualTo("1")
        }
    }
}
