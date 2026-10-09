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
package org.sonarlint.intellij.ai

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.sonarlint.intellij.AbstractSonarLintLightTests

class McpUiAdaptersTests : AbstractSonarLintLightTests() {
    @ParameterizedTest
    @ValueSource(strings = ["mcpServers", "servers"])
    fun `open selects the root SonarQube entry rather than unrelated project entries`(section: String) = ApplicationManager.getApplication().invokeAndWait {
        val content = """
            {
              "note": "é 🚀",
              "projects": {"example": {"mcpServers": {"sonarqube": {}}}},
              "$section": {
                // Other servers remain above SonarQube.
                "other": {},
                "sonarqube": {"command": "sonar", "args": ["run", "mcp"]},
              }
            }
        """.trimIndent()
        val file = createTestFile(".claude.json", content)

        IntellijMcpUiAdapter().openConfiguration(project, Path.of(file.path))

        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertThat(editor.caretModel.offset).isEqualTo(editor.document.text.indexOf("\"sonarqube\": {\"command\""))
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "not JSON", "{\"projects\": {\"example\": {\"mcpServers\": {\"sonarqube\": {}}}}}"])
    fun `open falls back to the beginning when no root SonarQube entry can be located`(content: String) = ApplicationManager.getApplication().invokeAndWait {
        val file = createTestFile(".claude.json", content)

        IntellijMcpUiAdapter().openConfiguration(project, Path.of(file.path))

        assertThat(FileEditorManager.getInstance(project).selectedTextEditor!!.caretModel.offset).isZero()
    }
}
