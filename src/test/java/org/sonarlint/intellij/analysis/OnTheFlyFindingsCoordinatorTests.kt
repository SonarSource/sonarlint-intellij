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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.vfs.VirtualFile
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.editor.EditorHighlightRefresh
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.filter.FilterCriteria
import org.sonarlint.intellij.ui.filter.FindingsFilter
import org.sonarlint.intellij.util.VirtualFileUtils
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto

class OnTheFlyFindingsCoordinatorTests : AbstractSonarLintLightTests() {

    private lateinit var restarter: CodeAnalyzerRestarter
    private lateinit var toolWindow: SonarLintToolWindow
    private lateinit var holder: OnTheFlyFindingsHolder
    private lateinit var coordinator: OnTheFlyFindingsCoordinator

    @BeforeEach
    fun prepare() {
        restarter = mock()
        toolWindow = mock()
        whenever(toolWindow.getCurrentFileFilterCriteria()).thenReturn(FilterCriteria())
        replaceProjectService(CodeAnalyzerRestarter::class.java, restarter)
        replaceProjectService(SonarLintToolWindow::class.java, toolWindow)
        holder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        coordinator = getService(project, OnTheFlyFindingsCoordinator::class.java)
        holder.clearAllCurrentFileFindings()
        reset(restarter)
        reset(toolWindow)
    }

    @Test
    fun should_highlight_on_first_analysis_when_current_file_tab_was_never_created() {
        val file = createAndOpenTestVirtualFile("Foo.java", "class Foo {}")
        val issue = mock<LiveIssue>()
        resetMocks()

        holder.updateOnAnalysisResult(analysisResult(file, issue))

        verify(restarter).refreshFiles(check { files: Collection<VirtualFile> -> assertThat(files).contains(file) })
        assertThat(holder.getIssuesForFile(file)).containsExactly(issue)
        verify(toolWindow).updateCurrentFileTab(anyOrNull())
    }

    @Test
    fun should_clear_markup_when_a_previously_highlighted_file_becomes_clean() {
        val file = createAndOpenTestVirtualFile("Foo.java", "class Foo {}")
        holder.updateOnAnalysisResult(analysisResult(file, mock()))
        resetMocks()

        holder.updateOnAnalysisResult(cleanAnalysisResult(file))

        verify(restarter).refreshFiles(check { files: Collection<VirtualFile> -> assertThat(files).contains(file) })
        assertThat(holder.getIssuesForFile(file)).isEmpty()
    }

    @Test
    fun should_not_refresh_markup_for_intermediate_publication() {
        val file = createAndOpenTestVirtualFile("Foo.java", "class Foo {}")
        resetMocks()

        holder.updateOnAnalysisIntermediateResult(
            AnalysisIntermediateResult(LiveFindings(mapOf(file to listOf(mock<LiveIssue>())), emptyMap()))
        )

        verifyNoInteractions(restarter)
        verify(toolWindow).updateCurrentFileTab(anyOrNull())
    }

    @Test
    fun should_clear_an_open_file_raised_with_an_empty_list_and_leave_omitted_uris_unchanged() {
        val fileA = createAndOpenTestVirtualFile("A.java", "class A {}")
        val fileB = createAndOpenTestVirtualFile("B.java", "class B {}")
        val issueA = mock<LiveIssue>()
        val issueB = mock<LiveIssue>()
        holder.updateOnAnalysisResult(
            AnalysisResult(
                null,
                LiveFindings(mapOf(fileA to listOf(issueA), fileB to listOf(issueB)), emptyMap()),
                listOf(fileA, fileB),
                Instant.EPOCH,
            )
        )
        resetMocks()
        val uriA = VirtualFileUtils.toURI(fileA)
        assertThat(uriA).isNotNull

        holder.updateViewsWithNewIssues(module, mapOf(uriA!! to emptyList<RaisedIssueDto>()))

        assertThat(holder.getIssuesForFile(fileA)).isEmpty()
        assertThat(holder.getIssuesForFile(fileB)).containsExactly(issueB)
        verify(restarter).refreshFiles(check { files: Collection<VirtualFile> ->
            assertThat(files).contains(fileA).doesNotContain(fileB)
        })
    }

    @Test
    fun should_clear_markup_on_a_background_open_file_that_became_clean() {
        val fileA = createAndOpenTestVirtualFile("A.java", "class A {}")
        val fileB = createAndOpenTestVirtualFile("B.java", "class B {}")
        val issueA = mock<LiveIssue>()
        holder.updateOnAnalysisResult(
            AnalysisResult(
                null,
                LiveFindings(mapOf(fileA to listOf(issueA), fileB to listOf(mock())), emptyMap()),
                listOf(fileA, fileB),
                Instant.EPOCH,
            )
        )
        resetMocks()

        holder.updateOnAnalysisResult(
            AnalysisResult(
                null,
                LiveFindings(mapOf(fileA to listOf(issueA)), emptyMap()),
                listOf(fileA, fileB),
                Instant.EPOCH,
            )
        )

        verify(restarter).refreshFiles(check { files: Collection<VirtualFile> -> assertThat(files).contains(fileB) })
        assertThat(holder.getIssuesForFile(fileB)).isEmpty()
        assertThat(holder.getIssuesForFile(fileA)).containsExactly(issueA)
    }

    @Test
    fun should_keep_unfiltered_findings_for_squiggles_when_the_panel_would_filter_the_list() {
        val file = createAndOpenTestVirtualFile("Foo.java", "class Foo {}")
        val issue = mock<LiveIssue>()
        whenever(issue.message).thenReturn("Remove this unused private field")
        whenever(issue.getRuleKey()).thenReturn("java:S1068")
        whenever(issue.file()).thenReturn(file)
        whenever(issue.isResolved()).thenReturn(false)
        resetMocks()

        holder.updateOnAnalysisResult(analysisResult(file, issue))

        val hiddenInTheList = FindingsFilter(project).filterAllFindings(
            file,
            FilterCriteria(textFilter = "does-not-match-the-issue"),
        )
        assertThat(hiddenInTheList.issues).isEmpty()
        assertThat(holder.getIssuesForFile(file)).containsExactly(issue)
        verify(toolWindow).updateCurrentFileTab(anyOrNull())
        verify(toolWindow, never()).refreshViews()
    }

    @Test
    fun should_not_refresh_markup_for_open_files_this_analysis_did_not_cover() {
        val fileA = createAndOpenTestVirtualFile("A.java", "class A {}")
        val fileB = createAndOpenTestVirtualFile("B.java", "class B {}")
        val issueA = mock<LiveIssue>()
        val issueB = mock<LiveIssue>()
        holder.updateOnAnalysisResult(
            AnalysisResult(
                null,
                LiveFindings(mapOf(fileA to listOf(issueA), fileB to listOf(issueB)), emptyMap()),
                listOf(fileA, fileB),
                Instant.EPOCH,
            )
        )
        resetMocks()

        holder.updateOnAnalysisResult(
            AnalysisResult(
                null,
                LiveFindings(mapOf(fileA to listOf(issueA)), emptyMap()),
                listOf(fileA),
                Instant.EPOCH,
            )
        )

        verify(restarter).refreshFiles(check { files: Collection<VirtualFile> ->
            assertThat(files).contains(fileA).doesNotContain(fileB)
        })
        assertThat(holder.getIssuesForFile(fileB)).containsExactly(issueB)
    }

    @Test
    fun should_highlight_open_files_when_the_binding_changes() {
        val file = createAndOpenTestVirtualFile("Foo.java", "class Foo {}")
        replaceProjectService(SonarLintToolWindow::class.java, SonarLintToolWindow(project))
        resetMocks()

        getService(project, SonarLintToolWindow::class.java).bindingChanged()

        verify(restarter).refreshFiles(check { files: Collection<VirtualFile> -> assertThat(files).contains(file) })
    }

    @Test
    fun should_honor_changed_files_without_findings_scope() {
        val fileA = createAndOpenTestVirtualFile("A.java", "class A {}")
        val fileB = createAndOpenTestVirtualFile("B.java", "class B {}")
        resetMocks()

        coordinator.applyHighlightRefresh(EditorHighlightRefresh.enabled(listOf(fileB)))

        verify(restarter).refreshFiles(check { files: Collection<VirtualFile> ->
            assertThat(files).contains(fileB).doesNotContain(fileA)
        })
    }

    @Test
    fun should_not_refresh_markup_when_highlight_refresh_is_disabled() {
        createAndOpenTestVirtualFile("Foo.java", "class Foo {}")
        resetMocks()

        coordinator.applyHighlightRefresh(EditorHighlightRefresh.NONE)

        verifyNoInteractions(restarter)
    }

    private fun resetMocks() {
        reset(restarter)
        reset(toolWindow)
    }

    private fun analysisResult(file: VirtualFile, issue: LiveIssue): AnalysisResult {
        return AnalysisResult(
            null,
            LiveFindings(mapOf(file to listOf(issue)), emptyMap()),
            listOf(file),
            Instant.EPOCH,
        )
    }

    private fun cleanAnalysisResult(file: VirtualFile): AnalysisResult {
        return AnalysisResult(null, LiveFindings.none(), listOf(file), Instant.EPOCH)
    }
}
