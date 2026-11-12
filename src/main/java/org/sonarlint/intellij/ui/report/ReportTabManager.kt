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

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.ui.ToolWindowConstants

/**
 * Service responsible for managing multiple report tabs in the SonarQube for IDE tool window.
 * 
 * <h3>Design & Architecture:</h3>
 * This service manages the lifecycle of dated report tabs, allowing multiple analysis results
 * to be displayed simultaneously. Each report tab is identified independently of its display timestamp and
 * can be closed independently.
 * 
 * <h3>Key Features:</h3>
 * - Dynamic Tab Creation: Creates new report tabs on demand when analysis results are available
 * - Date-based Naming: Each tab includes a timestamp for easy identification
 * - Multi-tab Support: Supports multiple concurrent report tabs
 * - Tab Cleanup: Automatically manages tab lifecycle and cleanup
 */
@Service(Service.Level.PROJECT)
class ReportTabManager(private val project: Project) {
    
    private data class ReportTab(val content: Content, val panel: ReportPanel)

    private val reportTabs = ConcurrentHashMap<String, ReportTab>()
    internal var clock: Clock = Clock.systemDefaultZone()
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
    
    /**
     * Creates a report tab immediately with loading state for the given batch.
     * This provides immediate feedback to the user that analysis has started.
     */
    @Synchronized
    fun createLoadingReportTab(batchId: String, expectedModuleCount: Int = 1): String? {
        val existingTab = reportTabs[batchId]
        if (existingTab != null) {
            // Tab already exists, just return the title
            return existingTab.content.displayName
        }
        
        val toolWindow = getToolWindow() ?: return null
        val contentManager = toolWindow.contentManager
        
        val timestamp = LocalDateTime.now(clock)
        val tabTitle = "Report - ${dateFormatter.format(timestamp)}"
        
        // Create new report panel in loading state
        val reportPanel = ReportPanel(project)
        reportPanel.showLoadingState(expectedModuleCount)
        
        // Create and add content to tool window
        val content = contentManager.factory.createContent(reportPanel, tabTitle, false).apply {
            isCloseable = true
            putUserData(REPORT_TAB_KEY, batchId)
        }
        
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
        
        // Store reference to panel and batch mapping
        reportTabs[batchId] = ReportTab(content, reportPanel)

        toolWindow.show()
        
        return tabTitle
    }

    /**
     * Updates an existing report tab or creates a new one for the given batch.
     * This allows incremental updates as analysis results become available.
     */
    @Synchronized
    fun updateOrCreateReportTab(batchId: String, analysisResult: AnalysisResult, completedModules: Int = 1, expectedModules: Int = 1): String? {
        val existingTab = reportTabs[batchId]
        
        return if (existingTab != null) {
            existingTab.panel.updateAnalysisProgress(completedModules, expectedModules)
            existingTab.panel.mergeAnalysisResults(analysisResult)
            existingTab.content.displayName
        } else {
            createReportTab(analysisResult, batchId)
        }
    }
    
    /**
     * Creates a new report tab with the current timestamp and displays the analysis results.
     */
    fun createReportTab(analysisResult: AnalysisResult): String? {
        return createReportTab(analysisResult, UUID.randomUUID().toString())
    }
    
    private fun createReportTab(analysisResult: AnalysisResult, batchId: String): String? {
        val toolWindow = getToolWindow() ?: return null
        val contentManager = toolWindow.contentManager
        
        val timestamp = LocalDateTime.now(clock)
        val tabTitle = "Report - ${dateFormatter.format(timestamp)}"
        
        // Create new report panel
        val reportPanel = ReportPanel(project)
        reportPanel.updateFindings(analysisResult)
        
        // Create and add content to tool window
        val content = contentManager.factory.createContent(reportPanel, tabTitle, false).apply {
            isCloseable = true
            putUserData(REPORT_TAB_KEY, batchId)
        }
        
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
        
        // Store reference to panel
        reportTabs[batchId] = ReportTab(content, reportPanel)

        toolWindow.show()
        
        return tabTitle
    }

    private fun getToolWindow(): ToolWindow? {
        return ToolWindowManager.getInstance(project).getToolWindow(ToolWindowConstants.TOOL_WINDOW_ID)
    }
    
    companion object {
        private val REPORT_TAB_KEY = Key.create<String>("SONARLINT_REPORT_TAB_KEY")
    }

}
