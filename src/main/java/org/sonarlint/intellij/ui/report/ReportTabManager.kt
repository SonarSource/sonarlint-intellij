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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.IdentityHashMap
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.ui.ToolWindowConstants

/** Manages dated report tabs and suppresses results for reports closed by the user. */
@Service(Service.Level.PROJECT)
class ReportTabManager(private val project: Project) : Disposable {

    private val reportTabs = IdentityHashMap<Content, ReportPanel>()
    private val batchToContent = mutableMapOf<String, Content>()
    // Keep only scalar IDs until project disposal: even duplicate completions must not reopen a report.
    private val closedBatches = mutableSetOf<String>()
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
    private var disposed = false

    @Synchronized
    fun createLoadingReportTab(batchId: String, expectedModuleCount: Int = 1): String? {
        if (disposed || project.isDisposed || batchId in closedBatches) return null
        batchToContent[batchId]?.let { return it.displayName }
        return createReportTab(batchId) { it.showLoadingState(expectedModuleCount) }
    }

    @Synchronized
    fun updateOrCreateReportTab(batchId: String, analysisResult: AnalysisResult, completedModules: Int = 1, expectedModules: Int = 1): String? {
        if (disposed || project.isDisposed || batchId in closedBatches) return null
        val content = batchToContent[batchId]
        if (content != null) {
            val panel = reportTabs[content] ?: return null
            panel.updateAnalysisProgress(completedModules, expectedModules)
            panel.mergeAnalysisResults(analysisResult)
            return content.displayName
        }
        return createReportTab(batchId) { it.updateFindings(analysisResult) }
    }

    @Synchronized
    fun createReportTab(analysisResult: AnalysisResult): String? =
        createReportTab(null) { it.updateFindings(analysisResult) }

    private fun createReportTab(batchId: String?, initialize: (ReportPanel) -> Unit): String? {
        if (disposed || project.isDisposed || batchId in closedBatches) return null
        val toolWindow = getToolWindow() ?: return null
        val contentManager = toolWindow.contentManager
        val tabTitle = "Report - ${dateFormatter.format(LocalDateTime.now())}"
        val panel = ReportPanel(project)
        val content = contentManager.factory.createContent(panel, tabTitle, false).apply { isCloseable = true }

        // Install ownership before adding content, since add/select can notify listeners synchronously.
        reportTabs[content] = panel
        batchId?.let { batchToContent[it] = content }
        val listener = object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content === content) closeReport(content)
            }
        }
        contentManager.addContentManagerListener(listener)
        Disposer.register(panel.lifetime, Disposable { contentManager.removeContentManagerListener(listener) })
        Disposer.register(content, Disposable { closeReport(content) })
        Disposer.register(panel.lifetime, Disposable { closeReport(content) })
        initialize(panel)
        contentManager.addContent(content)
        if (!reportTabs.containsKey(content)) return null
        contentManager.setSelectedContent(content)
        if (!reportTabs.containsKey(content)) return null
        toolWindow.show()
        return tabTitle
    }

    @Synchronized
    private fun closeReport(content: Content) {
        val batches = batchToContent.filterValues { it === content }.keys
        closedBatches.addAll(batches)
        batches.forEach(batchToContent::remove)
        reportTabs.remove(content)?.invalidateReport()
    }

    @Synchronized
    fun getOpenReportTabs(): Set<String> = reportTabs.keys.map { it.displayName }.toSet()

    private fun getToolWindow(): ToolWindow? =
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowConstants.TOOL_WINDOW_ID)

    @Synchronized
    override fun dispose() {
        disposed = true
        reportTabs.keys.toList().forEach(::closeReport)
        closedBatches.clear()
    }
}
