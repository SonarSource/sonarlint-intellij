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
package org.sonarlint.intellij.ui.report

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.UIUtil
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.SonarLintTestUtils.createIssue
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.analysis.AnalysisState
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.analysis.RunningAnalysesTracker
import org.sonarlint.intellij.callable.ShowReportCallable
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThreadAndWait
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto

class ReportCancellationTests : AbstractSonarLintLightTests() {
    private lateinit var panel: ReportPanel
    private lateinit var tracker: RunningAnalysesTracker

    @BeforeEach
    fun prepareReport() {
        replaceProjectService(AnalysisStatus::class.java, mock<AnalysisStatus>())
        tracker = RunningAnalysesTracker(project)
        replaceProjectService(RunningAnalysesTracker::class.java, tracker)
        replaceProjectService(CodeAnalyzerRestarter::class.java, mock<CodeAnalyzerRestarter>())
        onUiThread {
            panel = ReportPanel(project)
            Disposer.register(testRootDisposable, panel)
        }
        val reports = mock<ReportTabManager>()
        doAnswer { panel.showLoadingState(it.getArgument(1)); "report" }
            .whenever(reports).createLoadingReportTab(any(), any())
        doAnswer {
            panel.updateAnalysisProgress(it.getArgument(2), it.getArgument(3))
            panel.mergeAnalysisResults(it.getArgument(1))
            "report"
        }.whenever(reports).updateOrCreateReportTab(any(), any(), any(), any())
        doAnswer { panel.showAnalysisFailure(it.getArgument(1), it.getArgument(2)); null }
            .whenever(reports).reportModuleFailed(any(), any(), any())
        replaceProjectService(ReportTabManager::class.java, reports)
    }

    @Test
    fun conversion_cancellation_ends_report_loading_and_survives_refresh() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val uri = URI.create(myFixture.file.virtualFile.url)
        val callback = ShowReportCallable(project)
        flushUi()
        onUiThread { assertThat(analysisSpinners()).isNotEmpty() }
        val id = UUID.randomUUID()
        val state = AnalysisState(id, callback, module)
        tracker.track(state)
        val canceledIssue = mock<RaisedIssueDto>()
        whenever(canceledIssue.textRange).thenThrow(ProcessCanceledException())

        assertThatThrownBy {
            CompletableFuture.runAsync {
                state.addRawIssues(id, mapOf(uri to listOf(canceledIssue)), false)
            }.get(10, TimeUnit.SECONDS)
        }.hasCauseInstanceOf(ProcessCanceledException::class.java)
        flushUi()

        onUiThread {
            assertFailedReport()
            panel.refreshView()
            assertFailedReport()
        }
        assertThat(tracker.getById(id)).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun mixed_report_counts_each_module_once_and_preserves_successful_findings(failureFirst: Boolean) {
        val file = myFixture.configureByText("Sample.java", "class Sample {}").virtualFile
        val issue = LiveIssue(module, createIssue(1), file, emptyList())
        val result = AnalysisResult(UUID.randomUUID(), LiveFindings(mapOf(file to listOf(issue)), emptyMap()), listOf(file), Instant.now())
        val callback = ShowReportCallable(project, 3)
        flushUi()

        if (failureFirst) callback.onError(ProcessCanceledException()) else callback.onSuccess(result)
        flushUi()
        onUiThread { assertThat(visibleLabels()).contains("Analysis ongoing • 2 modules remaining") }
        if (failureFirst) callback.onSuccess(result) else callback.onError(ProcessCanceledException())
        flushUi()
        onUiThread { assertThat(visibleLabels()).contains("Analysis ongoing • 1 module remaining") }
        callback.onError(ProcessCanceledException())
        flushUi()

        onUiThread {
            assertThat(analysisSpinners()).isEmpty()
            assertThat(visibleLabels()).contains("Analysis incomplete. One or more modules could not be analyzed.")
            val issueNodes = UIUtil.findComponentsOfType(panel, JTree::class.java).flatMap { tree ->
                (tree.model.root as? DefaultMutableTreeNode)?.depthFirstEnumeration()?.toList()?.filterIsInstance<IssueNode>().orEmpty()
            }
            assertThat(issueNodes.map { it.finding }).containsExactly(issue)
        }
    }

    @Test
    fun all_canceled_modules_finish_loading_only_after_the_last_callback() {
        val callback = ShowReportCallable(project, 2)
        callback.onError(ProcessCanceledException())
        flushUi()
        onUiThread {
            assertThat(analysisSpinners()).isNotEmpty()
            assertThat(visibleLabels()).contains("Analysis ongoing • 1 module remaining")
        }
        callback.onError(ProcessCanceledException())
        flushUi()
        onUiThread { assertFailedReport() }
    }

    private fun assertFailedReport() {
        assertThat(analysisSpinners()).isEmpty()
        assertThat(visibleLabels()).contains("Analysis incomplete. One or more modules could not be analyzed.")
        assertThat(UIUtil.findComponentsOfType(panel, JBPanelWithEmptyText::class.java).map { it.emptyText.text })
            .contains("Analysis did not complete. Run the analysis again.")
    }

    private fun visibleLabels() = UIUtil.findComponentsOfType(panel, JBLabel::class.java).filter { it.isVisible }.map { it.text }

    private fun analysisSpinners() = UIUtil.findComponentsOfType(panel, AsyncProcessIcon::class.java).filter { it.isVisible }

    private fun flushUi() = onUiThread { UIUtil.dispatchAllInvocationEvents() }

    private fun onUiThread(action: () -> Unit) = runOnUiThreadAndWait(project, ModalityState.defaultModalityState()) { action() }
}
