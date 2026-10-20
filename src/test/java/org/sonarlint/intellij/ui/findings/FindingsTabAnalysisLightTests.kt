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
package org.sonarlint.intellij.ui.findings

import com.intellij.openapi.fileEditor.FileEditorManager
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.fixtures.LightTestProjectFiles

@DisabledOnOs(OS.WINDOWS)
class FindingsTabAnalysisLightTests : AbstractSonarLintUILightTests() {

    @Test
    fun should_show_one_issue_in_findings_tab_after_analysis() {
        val file = LightTestProjectFiles.registerFromTestData(module, testDataPath, "src/file.xml", ::createTestPsiFile)
        FileEditorManager.getInstance(project).openFile(file, true)

        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(mainToolWindow.findings.toolbar.isAnalyzeCurrentFileEnabled).isTrue()
        }

        mainToolWindow.findings.toolbar.analyzeCurrentFile()

        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            assertThat(mainToolWindow.findings.issues).hasSize(1)
        }

        assertThat(mainToolWindow.findings.findingsTree.summaryLabelText).isEqualTo("Found 1 issue")
        assertThat(mainToolWindow.findings.issues.single().ruleKey).isEqualTo("xml:S1778")
        assertThat(mainToolWindow.findings.toolbar.isVisible).isTrue()
    }
}
