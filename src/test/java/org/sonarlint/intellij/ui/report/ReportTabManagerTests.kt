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

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.ContentUI
import com.intellij.ui.content.impl.ContentManagerImpl
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.callable.ShowReportCallable
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.messages.ProjectConfigurationListener
import org.sonarlint.intellij.ui.WhatsInThisViewPanel
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.ToolWindowConstants

class ReportTabManagerTests : AbstractSonarLintLightTests() {
    private lateinit var manager: ReportTabManager
    private lateinit var contents: ContentManagerImpl
    private lateinit var toolWindow: ToolWindow

    @BeforeEach
    fun prepareReportTabs() {
        replaceApplicationService(BackendService::class.java, mock<BackendService>().apply {
            whenever(isAlive()).thenReturn(true)
        })
        val ui = mock<ContentUI>()
        whenever(ui.component).thenReturn(JPanel())
        whenever(ui.isSingleSelection).thenReturn(true)
        whenever(ui.canBeEmptySelection()).thenReturn(true)
        whenever(ui.canChangeSelectionTo(any(), any())).thenReturn(true)
        contents = ContentManagerImpl(ui, true, project, testRootDisposable)
        toolWindow = mock()
        whenever(toolWindow.contentManager).thenReturn(contents)
        whenever(toolWindow.anchor).thenReturn(ToolWindowAnchor.BOTTOM)
        val windows = mock<ToolWindowManager>()
        whenever(windows.getToolWindow(ToolWindowConstants.TOOL_WINDOW_ID)).thenReturn(toolWindow)
        replaceProjectService(ToolWindowManager::class.java, windows)
        manager = ReportTabManager(project)
        Disposer.register(testRootDisposable, manager)
        replaceProjectService(ReportTabManager::class.java, manager)
    }

    @Test
    fun `closing a running report suppresses every late and duplicate completion`() {
        val report = manager.createLoadingReportTab(3)!!
        val content = contents.getContent(0)!!
        val panel = content.component as ReportPanel
        assertThat(contents.removeContent(content, true)).isTrue()
        clearInvocations(toolWindow)

        repeat(4) { completion ->
            report.update(result(), completion + 1, 3)
        }
        assertThat(field(report, "panel")).isNull()
        drainEdt()

        assertThat(contents.contentCount).isZero()
        verify(toolWindow, never()).show()
        assertNoReportTabs()
        assertPanelCleared(panel)
        assertThat(project.isDisposed).isFalse()
    }

    @Test
    fun `repeated report closure releases panels while project remains open`() {
        repeat(12) {
            val report = manager.createLoadingReportTab()!!
            val content = contents.getContent(0)!!
            val panel = content.component as ReportPanel
            report.update(result())
            contents.removeContent(content, true)
            assertNoReportTabs()
            assertPanelCleared(panel)
            assertThat(field(report, "panel")).isNull()
        }
        drainEdt()
        assertNoReportTabs()
        assertThat(project.isDisposed).isFalse()
    }

    @Test
    fun `removal without content disposal ends the panel and queued tree work`() {
        val report = manager.createLoadingReportTab()!!
        val content = contents.getContent(0)!!
        val panel = content.component as ReportPanel
        val file = createTestFile("Report.java", "class Report {}")
        val issue = mock<LiveIssue>()
        whenever(issue.file()).thenReturn(file)
        whenever(issue.getRuleKey()).thenReturn("java:S1234")
        val analysis = AnalysisResult(null, LiveFindings(mapOf(file to listOf(issue)), emptyMap()), listOf(file), Instant.now())
        panel.updateFindings(analysis)
        panel.expandAllTrees()
        panel.collapseAllTrees()
        contents.removeContent(content, false)
        panel.mergeAnalysisResults(analysis)
        panel.updateAnalysisProgress(2, 3)
        panel.showLoadingState(3)
        panel.refreshView()
        panel.showFiltersPanel(true)
        panel.expandAllTrees()
        panel.collapseAllTrees()
        drainEdt()

        assertThat(Disposer.isDisposed(panel)).isTrue()
        assertThat(field(report, "panel")).isNull()
        assertThat(Disposer.isDisposed(content)).isFalse()
        assertPanelCleared(panel)
        assertThat(panel.isFiltersPanelVisible()).isFalse()
        assertNoReportTabs()
        Disposer.dispose(content)
        panel.dispose()
        assertNoReportTabs()
        assertThat(project.isDisposed).isFalse()
    }

    @Test
    fun `direct content and panel disposal both clean exact ownership`() {
        val firstReport = manager.createLoadingReportTab()!!
        val first = contents.getContent(0)!!
        Disposer.dispose(first)
        assertNoReportTabs()
        firstReport.update(result())
        assertThat(field(firstReport, "panel")).isNull()
        contents.removeContent(first, false)

        val secondReport = manager.createLoadingReportTab()!!
        val second = contents.getContent(0)!!
        Disposer.dispose(second.component as ReportPanel)
        assertNoReportTabs()
        secondReport.update(result())
        assertThat(field(secondReport, "panel")).isNull()
        contents.removeContent(second, true)
        assertThat(project.isDisposed).isFalse()
    }

    @Test
    fun `identical titles have independent ownership and the surviving report still merges`() {
        val firstReport = manager.createLoadingReportTab()!!
        val secondReport = manager.createLoadingReportTab()!!
        val first = contents.getContent(0)!!
        val second = contents.getContent(1)!!
        second.displayName = first.displayName
        contents.removeContent(first, true)
        val firstFile = createTestFile("first.txt", "first")
        val secondFile = createTestFile("second.txt", "second")
        val firstResult = result().copy(analyzedFiles = listOf(firstFile))
        val secondResult = result().copy(analyzedFiles = listOf(secondFile))
        firstReport.update(firstResult)
        secondReport.update(firstResult)
        secondReport.update(secondResult)
        drainEdt()

        val panel = second.component as ReportPanel
        assertThat((field(panel, "lastAnalysisResult") as AnalysisResult).analyzedFiles).containsExactly(firstFile, secondFile)
        assertThat(contents.contentCount).isEqualTo(1)
        assertThat(field(firstReport, "panel")).isNull()
        assertThat(field(secondReport, "panel")).isSameAs(panel)
    }

    @Test
    fun `reports opened with existing results also release their panel`() {
        val report = manager.createReportTab(result())!!
        val content = contents.getContent(0)!!
        val panel = content.component as ReportPanel
        contents.removeContent(content, true)
        drainEdt()
        assertNoReportTabs()
        assertPanelCleared(panel)
        assertThat(field(report, "panel")).isNull()
    }

    @Test
    fun `callbacks created together get independent reports`() {
        val first = ShowReportCallable(project)
        val second = ShowReportCallable(project)
        drainEdt()
        assertThat(contents.contentCount).isEqualTo(2)
        assertThat(field(first, "report")).isNotSameAs(field(second, "report"))
    }

    @Test
    fun `callback retries report creation when the tool window becomes available`() {
        val windows = getService(project, ToolWindowManager::class.java)
        whenever(windows.getToolWindow(ToolWindowConstants.TOOL_WINDOW_ID)).thenReturn(null)
        val callback = ShowReportCallable(project, 3)
        drainEdt()
        callback.onSuccess(result())
        drainEdt()
        assertThat(contents.contentCount).isZero()

        whenever(windows.getToolWindow(ToolWindowConstants.TOOL_WINDOW_ID)).thenReturn(toolWindow)
        callback.onSuccess(result())
        drainEdt()

        val panel = contents.getContent(0)!!.component as ReportPanel
        assertThat(field(panel, "receivedModuleCount")).isEqualTo(2)
        assertThat(field(panel, "expectedModuleCount")).isEqualTo(3)
    }

    @Test
    fun `a report closed synchronously during creation stays closed`() {
        val closeOnAdd = object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                contents.removeContent(event.content, true)
            }
        }
        contents.addContentManagerListener(closeOnAdd)
        val callback = ShowReportCallable(project, 2)
        drainEdt()
        contents.removeContentManagerListener(closeOnAdd)
        clearInvocations(toolWindow)
        callback.onSuccess(result())
        drainEdt()

        assertThat(contents.contentCount).isZero()
        assertThat(field(field(callback, "report")!!, "panel")).isNull()
        verify(toolWindow, never()).show()
    }

    @Test
    fun `disposing the service releases open panels and their callback handles`() {
        val report = manager.createLoadingReportTab()!!
        val panel = contents.getContent(0)!!.component as ReportPanel
        Disposer.dispose(manager)
        report.update(result())
        drainEdt()

        assertNoReportTabs()
        assertPanelCleared(panel)
        assertThat(field(report, "panel")).isNull()
        assertThat(manager.createLoadingReportTab()).isNull()
    }


    @Test
    fun `help work and configuration subscriptions end with the report owner`() {
        val requests = AtomicInteger()
        val bindings = mock<ProjectBindingManager>()
        val connection = CompletableFuture<Optional<ServerConnection>>()
        whenever(bindings.tryGetServerConnection()).doAnswer {
            requests.incrementAndGet()
            connection.join()
        }
        replaceProjectService(ProjectBindingManager::class.java, bindings)
        val owner = Disposer.newDisposable()
        Disposer.register(testRootDisposable, owner)
        val help = WhatsInThisViewPanel(project, "Report help", owner)
        PlatformTestUtil.waitWithEventsDispatching("Help request", { requests.get() == 1 }, 2)
        // Queue a card switch as well as leaving its original pooled lookup in flight.
        WhatsInThisViewPanel::class.java.getDeclaredMethod("switchCard", String::class.java).apply {
            isAccessible = true
        }.invoke(help, "Connected")
        Disposer.dispose(owner)
        connection.complete(Optional.of(mock()))
        project.messageBus.syncPublisher(ProjectConfigurationListener.TOPIC).changed(projectSettings)
        project.messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(globalSettings, globalSettings)
        drainEdt()

        assertThat(requests.get()).isEqualTo(1)
        assertThat(help.panel.components[0].isVisible).isTrue()
        assertThat(help.panel.components[1].isVisible).isFalse()
        assertThat(project.isDisposed).isFalse()
    }


    @Test
    fun `late callback successes keep the report closed and preserve editor refresh`() {
        val restarter = mock<CodeAnalyzerRestarter>()
        replaceProjectService(CodeAnalyzerRestarter::class.java, restarter)
        val callback = ShowReportCallable(project, 2)
        drainEdt()
        contents.removeContent(contents.getContent(0)!!, true)
        clearInvocations(restarter, toolWindow)
        callback.onSuccess(result())
        callback.onSuccess(result())
        callback.onSuccess(result())
        drainEdt()

        assertNoReportTabs()
        assertThat(contents.contentCount).isZero()
        verify(restarter, times(3)).refreshOpenFiles()
        verify(toolWindow, never()).show()
        assertThat(project.isDisposed).isFalse()
    }

    private fun result() = AnalysisResult(null, LiveFindings.none(), emptyList(), Instant.now())

    private fun drainEdt() = PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    private fun assertNoReportTabs() {
        assertThat(contents.contents.filter { it.component is ReportPanel && !Disposer.isDisposed(it.component as ReportPanel) }).isEmpty()
    }

    private fun assertPanelCleared(panel: ReportPanel) {
        assertThat(field(panel, "lastAnalysisResult")).isNull()
        assertThat(field(panel, "loadingPanel")).isNull()
        assertThat(field(panel, "loadingIcon")).isNull()
        val trees = field(panel, "treeManager") as ReportTreeManager
        assertThat(trees.issuesTreeBuilder.model.root as javax.swing.tree.TreeNode).matches { it.childCount == 0 }
        assertThat(trees.oldIssuesTreeBuilder.model.root as javax.swing.tree.TreeNode).matches { it.childCount == 0 }
        assertThat(trees.securityHotspotsTreeBuilder.isEmpty()).isTrue()
        assertThat(trees.oldSecurityHotspotsTreeBuilder.isEmpty()).isTrue()
        assertThat(trees.taintsTreeBuilder.isEmpty()).isTrue()
        assertThat(trees.oldTaintsTreeBuilder.isEmpty()).isTrue()
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
