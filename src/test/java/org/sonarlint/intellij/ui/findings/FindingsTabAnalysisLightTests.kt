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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.analysis.LightTestAnalysis
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.filter.FilterCriteria
import org.sonarlint.intellij.ui.filter.FindingsFilter

class FindingsTabAnalysisLightTests : AbstractSonarLintUILightTests() {

    private lateinit var analysis: LightTestAnalysis

    @BeforeEach
    fun waitForReadyAndAnalysisHelper() {
        analysis = LightTestAnalysis(project, module, testDataPath, ::createTestPsiFile)
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
            val readiness = getService(project, AnalysisReadinessCache::class.java)
            assertThat(readiness.isModuleReady(module)).isTrue()
            assertThat(readiness.isProjectReady).isTrue()
        }
    }

    @Test
    fun should_show_one_issue_in_findings_tab_after_analysis() {
        val file = analysis.registerFileFromTestData("src/file.xml")
        readOnEdt { FileEditorManager.getInstance(project).openFile(file, true) }
        val analyzed = analysis.runOnTheFlyAnalysis(file)

        assertThat(getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder.getIssuesForFile(analyzed.file))
            .hasSize(analyzed.issueCount)
        assertThat(FindingsFilter(project).filterAllFindings(analyzed.file, FilterCriteria()).issues)
            .hasSize(analyzed.issueCount)

        assertThat(mainToolWindow.findings.issues).hasSize(analyzed.issueCount)
        assertThat(mainToolWindow.findings.findingsTree.summaryLabelText).isEqualTo("Found 1 issue")
        assertThat(mainToolWindow.findings.issues.single().ruleKey).isEqualTo("xml:S1778")
        assertThat(mainToolWindow.findings.toolbar.isVisible).isTrue()
    }
}
