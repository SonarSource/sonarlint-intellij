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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.ui.ToolWindowConstants

/** Creates dated report tabs. Each analysis callback keeps its own report handle. */
@Service(Service.Level.PROJECT)
class ReportTabManager(private val project: Project) : Disposable {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
    @Volatile
    private var disposed = false

    fun createLoadingReportTab(expectedModuleCount: Int = 1): ReportTab? =
        createReportTab { it.showLoadingState(expectedModuleCount) }

    fun createReportTab(analysisResult: AnalysisResult): ReportTab? =
        createReportTab { it.updateFindings(analysisResult) }

    private fun createReportTab(initialize: (ReportPanel) -> Unit): ReportTab? {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (disposed || project.isDisposed) return null
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowConstants.TOOL_WINDOW_ID) ?: return null
        val contentManager = toolWindow.contentManager
        val panel = ReportPanel(project)
        Disposer.register(this, panel)
        val report = ReportTab(panel)
        val tabTitle = "Report - ${dateFormatter.format(LocalDateTime.now())}"
        val content = contentManager.factory.createContent(panel, tabTitle, false).apply { isCloseable = true }
        val listener = object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                // Removal can happen without content disposal; either path ends the report.
                if (event.content === content) Disposer.dispose(panel)
            }
        }
        contentManager.addContentManagerListener(listener)
        Disposer.register(panel, Disposable { contentManager.removeContentManagerListener(listener) })
        initialize(panel)
        contentManager.addContent(content)
        // Content listeners can close the report synchronously during add/select.
        if (report.isOpen) contentManager.setSelectedContent(content)
        if (report.isOpen) toolWindow.show()
        return report
    }

    override fun dispose() {
        disposed = true
    }

    /** A closed handle retains no panel and ignores all later module completions. */
    class ReportTab internal constructor(panel: ReportPanel) {
        @Volatile
        private var panel: ReportPanel? = panel
        internal val isOpen: Boolean get() = panel != null

        init {
            Disposer.register(panel, Disposable { this.panel = null })
        }

        fun update(analysisResult: AnalysisResult, completedModules: Int = 1, expectedModules: Int = 1) {
            ApplicationManager.getApplication().assertIsDispatchThread()
            panel?.let {
                it.updateAnalysisProgress(completedModules, expectedModules)
                it.mergeAnalysisResults(analysisResult)
            }
        }
    }
}
