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

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.currentfile.CurrentFileDisplayedFindingsStore
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails

class DirectHighlighterTests : AbstractSonarLintLightTests() {

    @Test
    fun should_not_fail_when_no_files() {
        getService(project, DirectHighlighter::class.java).updateHighlights(emptyList())
    }

    @Test
    fun should_apply_sonarlint_highlighters_for_displayed_findings() {
        val content = "class Foo {}"
        val file = createAndOpenTestPsiFile("Foo.java", content).virtualFile
        val issueMessage = "Remove this unused class"
        val expectedRange = textRangeOf(content, "Foo")
        seedDisplayedIssue(file, content, issueMessage)

        withOpenEditor(file) {
            val highlighter = getService(project, DirectHighlighter::class.java)
            highlighter.applyHighlightsForTest(file)

            val highlights = sonarLintHighlights(file, issueMessage)
            assertThat(highlights).hasSize(1)
            assertThat(highlights.single().startOffset).isEqualTo(expectedRange.first)
            assertThat(highlights.single().endOffset).isEqualTo(expectedRange.second)
        }
    }

    @Test
    fun should_clear_sonarlint_highlighters_when_findings_are_removed() {
        val content = "class Foo {}"
        val file = createAndOpenTestPsiFile("Foo.java", content).virtualFile
        val issueMessage = "Remove this unused class"
        seedDisplayedIssue(file, content, issueMessage)

        val highlighter = getService(project, DirectHighlighter::class.java)
        withOpenEditor(file) {
            highlighter.applyHighlightsForTest(file)
            assertThat(sonarLintHighlights(file, issueMessage)).hasSize(1)

            getService(project, CurrentFileDisplayedFindingsStore::class.java)
                .setSnapshot(FilteredFindings(emptyList(), emptyList(), emptyList(), emptyList()))
            highlighter.applyHighlightsForTest(file)

            assertThat(sonarLintHighlights(file, issueMessage)).isEmpty()
        }
    }

    @Test
    fun should_apply_highlights_asynchronously_via_updateHighlights() {
        val content = "class Foo {}"
        val file = createAndOpenTestPsiFile("Foo.java", content).virtualFile
        val issueMessage = "Remove this unused class"
        seedDisplayedIssue(file, content, issueMessage)

        withOpenEditor(file) {
            ApplicationManager.getApplication().executeOnPooledThread {
                getService(project, DirectHighlighter::class.java).updateHighlights(listOf(file))
            }

            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5)
            while (System.currentTimeMillis() < deadline) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                if (sonarLintHighlights(file, issueMessage).isNotEmpty()) {
                    break
                }
                Thread.sleep(50)
            }

            assertThat(sonarLintHighlights(file, issueMessage)).hasSize(1)
        }
    }

    private fun seedDisplayedIssue(file: VirtualFile, content: String, message: String) {
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val (startOffset, endOffset) = textRangeOf(content, "Foo")
        val rangeMarker = document.createRangeMarker(startOffset, endOffset)
        val issueDto = mock<RaisedIssueDto>()
        whenever(issueDto.id).thenReturn(UUID.randomUUID())
        whenever(issueDto.primaryMessage).thenReturn(message)
        whenever(issueDto.ruleKey).thenReturn("java:S1068")
        whenever(issueDto.severityMode).thenReturn(Either.forLeft(StandardModeDetails(IssueSeverity.MAJOR, RuleType.CODE_SMELL)))
        whenever(issueDto.isResolved).thenReturn(false)
        whenever(issueDto.isOnNewCode).thenReturn(false)

        val issue = LiveIssue(module, issueDto, file, rangeMarker, null, emptyList())
        getService(project, CurrentFileDisplayedFindingsStore::class.java)
            .setSnapshot(FilteredFindings(listOf(issue), emptyList(), emptyList(), emptyList()))
    }

    private fun textRangeOf(content: String, token: String): Pair<Int, Int> {
        val startOffset = content.indexOf(token)
        return startOffset to startOffset + token.length
    }

    private fun withOpenEditor(file: VirtualFile, block: () -> Unit) {
        FileEditorManager.getInstance(project).openFile(file, true)
        assertThat(FileEditorManager.getInstance(project).selectedTextEditor).isNotNull
        block()
    }

    private fun sonarLintHighlights(file: VirtualFile, expectedMessage: String): List<HighlightInfo> {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val highlights = mutableListOf<HighlightInfo>()
        DaemonCodeAnalyzerEx.processHighlights(document, project, null, 0, document.textLength) { info ->
            if (info.description == expectedMessage) {
                highlights.add(info)
            }
            true
        }
        return highlights
    }
}
