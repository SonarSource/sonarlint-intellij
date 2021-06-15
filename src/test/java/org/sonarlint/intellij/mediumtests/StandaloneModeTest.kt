/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.mediumtests

import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Before
import org.junit.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisCallback
import org.sonarlint.intellij.core.SonarLintEngineManager
import org.sonarlint.intellij.issue.IssueManager
import org.sonarlint.intellij.issue.LiveIssue
import org.sonarlint.intellij.trigger.SonarLintSubmitter
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarlint.intellij.util.SonarLintUtils.getService

class StandaloneModeTest : AbstractSonarLintLightTests() {

    @Before
    fun prepare() {
        getService(SonarLintEngineManager::class.java).stopAllEngines()
    }

    @Test
    fun should_analyze_java_file() {
        val fileToAnalyze = myFixture.configureByFile("src/Main.java").virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.ruleName },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("java:S1220", "The default unnamed package should not be used", null),
                tuple("java:S106", "Standard outputs should not be used directly to log anything", Pair(67, 77))
            )
    }

    @Test
    fun should_find_cross_file_python_issue() {
        val fileToAnalyze = myFixture.configureByFiles("src/main.py", "src/mod.py").first().virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.ruleName },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "The number and name of arguments passed to a function should match its parameters", Pair(45, 48))
            )
    }

    @Test
    fun should_find_cross_file_python_issue_after_module_file_is_modified_in_editor() {
        // first one is opened in the current editor
        val files = myFixture.configureByFiles("src/mod.py", "src/main.py")
        val moduleFile = files[0].virtualFile
        val fileToAnalyze = files[1].virtualFile
        // trigger a first analysis to build the table
        analyze(fileToAnalyze)

        myFixture.saveText(moduleFile, "def add(x): return x\n")
        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.ruleName },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "The number and name of arguments passed to a function should match its parameters", Pair(21, 24))
            )
    }

    private fun analyze(fileToAnalyze: VirtualFile): List<LiveIssue> {
        val submitter = getService(project, SonarLintSubmitter::class.java)
        submitter.submitFiles(setOf(fileToAnalyze), TriggerType.ACTION, EmptyAnalysisCallback(), false)
        return getService(project, IssueManager::class.java).getForFile(fileToAnalyze).toList()
    }

    class EmptyAnalysisCallback : AnalysisCallback {
        override fun onSuccess(failedVirtualFiles: MutableSet<VirtualFile>) {
        }

        override fun onError(e: Throwable) {
        }

    }
}
