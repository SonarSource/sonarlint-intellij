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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesCache
import org.sonarlint.intellij.ui.filter.FilterCriteria
import org.sonarlint.intellij.ui.filter.FindingsFilter
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto
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

            getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
                .replaceIssuesForFile(file, emptyList())
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

    @Test
    fun should_not_apply_a_prepared_result_after_a_newer_refresh_was_requested() {
        val content = "class Foo {}"
        val file = createAndOpenTestPsiFile("Foo.java", content).virtualFile
        val executor = ScheduledThreadPoolExecutor(1)
        val highlighter = DirectHighlighter(project, executor, 0)

        try {
            withOpenEditor(file) {
                val obsoleteMessage = "Obsolete finding"
                seedDisplayedIssue(file, content, obsoleteMessage)
                highlighter.updateHighlights(listOf(file))
                awaitQueue(executor)

                val latestMessage = "Latest finding"
                seedDisplayedIssue(file, content, latestMessage)
                highlighter.updateHighlights(listOf(file))
                awaitQueue(executor)

                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

                assertThat(sonarLintHighlights(file, obsoleteMessage)).isEmpty()
                assertThat(sonarLintHighlights(file, latestMessage)).hasSize(1)
            }
        } finally {
            highlighter.dispose()
        }
    }

    @Test
    fun should_recompute_a_prepared_result_when_the_document_changes_before_application() {
        val content = "class Foo {}"
        val insertedText = "// comment\n"
        val file = createAndOpenTestPsiFile("Foo.java", content).virtualFile
        val issueMessage = "Finding with a moving range"
        seedDisplayedIssue(file, content, issueMessage)
        val executor = ScheduledThreadPoolExecutor(1)
        val highlighter = DirectHighlighter(project, executor, 0)

        try {
            withOpenEditor(file) {
                highlighter.updateHighlights(listOf(file))
                awaitQueue(executor)

                val document = FileDocumentManager.getInstance().getDocument(file)!!
                WriteCommandAction.runWriteCommandAction(project) {
                    document.insertString(0, insertedText)
                }

                // The queued EDT application detects the new document revision and requests a fresh preparation.
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
                awaitQueue(executor)
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

                val expectedRange = textRangeOf(insertedText + content, "Foo")
                val highlight = sonarLintHighlights(file, issueMessage).single()
                assertThat(highlight.startOffset).isEqualTo(expectedRange.first)
                assertThat(highlight.endOffset).isEqualTo(expectedRange.second)
            }
        } finally {
            highlighter.dispose()
        }
    }

    @Test
    fun should_highlight_taints_from_the_taint_cache_without_a_findings_store() {
        val content = "class Foo {}"
        val file = createAndOpenTestPsiFile("Foo.java", content).virtualFile
        val issueMessage = "SQL injection"
        val expectedRange = textRangeOf(content, "Foo")
        seedTaint(file, content, issueMessage)

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
    fun should_keep_editor_squiggle_when_a_list_filter_would_hide_the_issue() {
        val content = "class Foo {}"
        val file = createAndOpenTestPsiFile("Foo.java", content).virtualFile
        val issueMessage = "Remove this unused private field"

        seedDisplayedIssue(file, content, issueMessage)

        val hiddenInList = FindingsFilter(project).filterAllFindings(
            file,
            FilterCriteria(textFilter = "does-not-match-the-issue"),
        )
        assertThat(hiddenInList.issues).isEmpty()

        withOpenEditor(file) {
            val highlighter = getService(project, DirectHighlighter::class.java)
            highlighter.applyHighlightsForTest(file)

            val highlights = sonarLintHighlights(file, issueMessage)
            assertThat(highlights).hasSize(1)
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
        getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder.replaceIssuesForFile(file, listOf(issue))
    }

    private fun seedTaint(file: VirtualFile, content: String, message: String) {
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val (startOffset, endOffset) = textRangeOf(content, "Foo")
        val rangeMarker = document.createRangeMarker(startOffset, endOffset)
        val dto = Mockito.mock(TaintVulnerabilityDto::class.java, Mockito.RETURNS_DEEP_STUBS)
        whenever(dto.message).thenReturn(message)
        whenever(dto.introductionDate).thenReturn(Instant.EPOCH)
        whenever(dto.isAiCodeFixable).thenReturn(false)
        whenever(dto.isOnNewCode).thenReturn(false)
        whenever(dto.severityMode).thenReturn(Either.forLeft(StandardModeDetails(IssueSeverity.MAJOR, RuleType.VULNERABILITY)))
        whenever(dto.ruleKey).thenReturn("javasecurity:S3649")
        whenever(dto.id).thenReturn(UUID.randomUUID())
        whenever(dto.sonarServerKey).thenReturn("taint-key")
        val taint = LocalTaintVulnerability(
            module,
            Location(file, rangeMarker, message, null, null),
            emptyList(),
            dto,
            false,
        )
        getService(project, TaintVulnerabilitiesCache::class.java).taintVulnerabilities = listOf(taint)
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

    private fun awaitQueue(executor: ScheduledThreadPoolExecutor) {
        executor.submit {}.get(5, TimeUnit.SECONDS)
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
