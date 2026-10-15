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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarlint.intellij.ui.filter.FiltersPanel
import org.sonarlint.intellij.ui.filter.MqrImpactFilter
import org.sonarlint.intellij.ui.filter.SeverityFilter
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.util.runOnPooledThread

class ReportPanelTests : AbstractSonarLintLightTests() {
    private lateinit var panel: ReportPanel
    private lateinit var trees: ReportTreeManager
    private lateinit var filters: FiltersPanel

    @BeforeEach
    fun preparePanel() {
        replaceApplicationService(BackendService::class.java, mock<BackendService>().apply {
            whenever(isAlive()).thenReturn(true)
        })
        panel = ReportPanel(project)
        Disposer.register(testRootDisposable, panel)
        trees = field(panel, "treeManager") as ReportTreeManager
        filters = field(panel, "filtersPanel") as FiltersPanel
        drainEdt()
    }

    @Test
    fun `filtering and splitting run off EDT while models are updated on EDT`() = withQueuedFiltering { tasks ->
        globalSettings.isFocusOnNewCode = true
        val file = createTestFile("Report.java", "class Report {}")
        val issue = issue(file, "Finding")
        whenever(issue.isMqrMode).doAnswer {
            assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
            true
        }
        whenever(issue.isResolved()).doAnswer {
            assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
            false
        }
        whenever(issue.isOnNewCode()).doAnswer {
            assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
            true
        }
        var modelChanges = 0
        trees.issuesTreeBuilder.model.addTreeModelListener(object : TreeModelListener {
            override fun treeNodesChanged(event: TreeModelEvent) = Unit
            override fun treeNodesInserted(event: TreeModelEvent) = Unit
            override fun treeNodesRemoved(event: TreeModelEvent) = Unit
            override fun treeStructureChanged(event: TreeModelEvent) {
                ApplicationManager.getApplication().assertIsDispatchThread()
                modelChanges++
            }
        })

        panel.updateFindings(result(file, issue))
        verify(issue, never()).isResolved()
        assertThat(displayedIssues()).isEmpty()
        runNext(tasks)
        assertThat(displayedIssues()).isEmpty()
        drainEdt()
        assertThat(displayedIssues()).containsExactly(issue)
        assertThat(modelChanges).isPositive()

        panel.refreshView()
        assertThat(tasks).hasSize(1)
        runNext(tasks)
        drainEdt()
        assertThat(displayedIssues()).containsExactly(issue)
    }

    @Test
    fun `new analysis supersedes a result already queued for display`() = withQueuedFiltering { tasks ->
        val file = createTestFile("Report.java", "class Report {}")
        val first = issue(file, "Old finding")
        val second = issue(file, "New finding")
        panel.updateFindings(result(file, first))
        runNext(tasks)
        panel.updateFindings(result(file, second))
        drainEdt()
        assertThat(displayedIssues()).isEmpty()

        runNext(tasks)
        drainEdt()
        assertThat(displayedIssues()).containsExactly(second)
    }

    @Test
    fun `module progress stays accurate when results arrive before the first repaint`() = withQueuedFiltering { tasks ->
        val file = createTestFile("Report.java", "class Report {}")
        panel.showLoadingState(3)
        panel.updateAnalysisProgress(1, 3)
        panel.updateFindings(result(file, issue(file, "First module")))
        panel.updateAnalysisProgress(2, 3)
        panel.mergeAnalysisResults(result(file, issue(file, "Second module")))
        runNext(tasks)
        runNext(tasks)
        drainEdt()

        assertThat(field(panel, "receivedModuleCount")).isEqualTo(2)
        assertThat(field(panel, "isLoadingState")).isEqualTo(false)
        assertThat(field(panel, "analysisStatusPanel")).isNotNull()
        assertThat((field(panel, "analysisStatusLabel") as javax.swing.JLabel).text)
            .isEqualTo("Analysis ongoing • 1 module remaining")
    }

    @Test
    fun `new filters supersede an already computed view`() = withQueuedFiltering { tasks ->
        val file = createTestFile("Report.java", "class Report {}")
        val first = issue(file, "Keep this finding")
        val second = issue(file, "Hide this finding")
        panel.updateFindings(result(file, first, second))
        runNext(tasks)
        filters.searchField.text = "Keep"
        drainEdt()
        assertThat(displayedIssues()).isEmpty()

        runNext(tasks)
        drainEdt()
        assertThat(displayedIssues()).containsExactly(first)
        assertThat((field(panel, "filteredFindingsCache") as FilteredFindings).issues).containsExactly(first)
    }

    @Test
    fun `closing the report rejects a computed view waiting on EDT`() = withQueuedFiltering { tasks ->
        val file = createTestFile("Report.java", "class Report {}")
        panel.updateFindings(result(file, issue(file, "Finding")))
        runNext(tasks)
        Disposer.dispose(panel)
        drainEdt()

        assertThat(displayedIssues()).isEmpty()
        assertThat((field(panel, "filteredFindingsCache") as FilteredFindings).isEmpty()).isTrue()
        assertThat(field(panel, "lastAnalysisResult")).isNull()
    }

    @Test
    fun `closing the report drops filtering that has not started`() = withQueuedFiltering { tasks ->
        val file = createTestFile("Report.java", "class Report {}")
        val issue = issue(file, "Finding")
        panel.updateFindings(result(file, issue))
        Disposer.dispose(panel)
        runNext(tasks)
        drainEdt()

        verify(issue, never()).isResolved()
        assertThat(displayedIssues()).isEmpty()
    }

    @Test
    fun `mode changes capture the reset severity filter before filtering`() = withQueuedFiltering { tasks ->
        val file = createTestFile("Report.java", "class Report {}")
        val issue = issue(file, "Standard finding")
        whenever(issue.isMqrMode).thenReturn(false)
        filters.severityCombo.selectedItem = MqrImpactFilter.HIGH
        panel.updateFindings(result(file, issue))
        runNext(tasks)
        drainEdt()
        assertThat(filters.severityCombo.selectedItem).isEqualTo(SeverityFilter.NO_FILTER)
        assertThat(tasks).hasSize(1)

        runNext(tasks)
        drainEdt()
        assertThat(displayedIssues()).containsExactly(issue)
        assertThat(tasks).isEmpty()
    }

    private fun withQueuedFiltering(test: (ArrayDeque<Runnable>) -> Unit) {
        val tasks = ArrayDeque<Runnable>()
        // Static mocks affect this EDT only; computations run on a real background thread.
        mockStatic(Class.forName("org.sonarlint.intellij.util.ThreadUtilsKt")).use { pooledTasks ->
            pooledTasks.`when`<Unit> { runOnPooledThread(eq(project), any()) }.thenAnswer { invocation ->
                tasks.addLast(invocation.getArgument(1))
                null
            }
            test(tasks)
        }
    }

    private fun runNext(tasks: ArrayDeque<Runnable>) {
        CompletableFuture.runAsync(tasks.removeFirst()).get(5, TimeUnit.SECONDS)
    }

    private fun issue(file: VirtualFile, message: String): LiveIssue = mock<LiveIssue>().apply {
        whenever(file()).thenReturn(file)
        whenever(getRuleKey()).thenReturn("java:S1234")
        whenever(this.message).thenReturn(message)
        whenever(isMqrMode).thenReturn(true)
    }

    private fun result(file: VirtualFile, vararg issues: LiveIssue) =
        AnalysisResult(null, LiveFindings(mapOf(file to issues.toList()), emptyMap()), listOf(file), Instant.now())

    private fun displayedIssues(): List<LiveIssue> =
        (trees.issuesTreeBuilder.model.root as javax.swing.tree.DefaultMutableTreeNode).depthFirstEnumeration()
            .toList().filterIsInstance<IssueNode>().map { it.issue() }

    private fun drainEdt() = PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
