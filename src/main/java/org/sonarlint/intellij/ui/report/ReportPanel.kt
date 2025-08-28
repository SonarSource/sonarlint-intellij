/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.JScrollPane
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.actions.ShowReportFiltersAction
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.ui.FindingDetailsPanel
import org.sonarlint.intellij.ui.FindingKind
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.factory.PanelFactory.Companion.createSplitter
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarlint.intellij.ui.filter.FiltersPanel
import org.sonarlint.intellij.ui.filter.FindingsFilter
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.util.SonarLintActions
import org.sonarlint.intellij.util.runOnPooledThread

// UI Configuration
const val SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION"
const val TOOLBAR_ID = "SonarQube for IDE"
const val DEFAULT_SPLIT_PROPORTION = 0.5f
val FINDING_DETAILS_MINIMUM_SIZE = Dimension(350, 200)

// Layout
const val VERTICAL_FLOW_LAYOUT_HGAP = 0
const val VERTICAL_FLOW_LAYOUT_VGAP = 0

class ReportPanel(private val project: Project) : SimpleToolWindowPanel(false, false), Disposable {
    
    // Components
    private val whatsNewPanel = ReportTabStatusPanel(project)
    private val restartSonarLintAction = SonarLintActions.getInstance().restartSonarLintAction()
    
    // Core managers
    private val findingDetailsPanel = FindingDetailsPanel(project, this, FindingKind.MIX)
    private val filtersPanel = createFiltersPanel()
    private val findingsFilter = FindingsFilter(project)
    private val displayManager = ReportDisplayManager(filtersPanel)
    private val treeManager = ReportTreeManager(project, findingDetailsPanel)
    
    // UI components
    private lateinit var findingsPanel: JBPanelWithEmptyText
    private lateinit var findingsTreePane: JScrollPane
    private lateinit var headerCardPanel: JBPanel<*>
    private lateinit var mainToolbar: ActionToolbar
    
    // State
    private var lastAnalysisResult: AnalysisResult? = null
    private var filteredFindingsCache = FilteredFindings(emptyList(), emptyList(), emptyList(), emptyList())

    init {
        initializeUI()
        configureInitialState()
    }

    fun updateFindings(analysisResult: AnalysisResult) {
        if (project.isDisposed) return

        lastAnalysisResult = analysisResult
        val isFocusOnNewCode = getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()

        applyFiltering()
        val findings = ReportFilteringUtils.convertFilteredFindingsToMap(filteredFindingsCache)

        updateTreeModels(findings, isFocusOnNewCode)
        treeManager.configureTreeVisibility(isFocusOnNewCode)
        treeManager.expandTrees()

        showFindingsState()
    }

    fun showFiltersPanel(show: Boolean) {
        filtersPanel.isVisible = show
        headerCardPanel.revalidate()
        headerCardPanel.repaint()
    }

    fun isFiltersPanelVisible(): Boolean = filtersPanel.isVisible

    fun refreshView() {
        lastAnalysisResult?.let(::updateFindings) ?: showEmptyState()
    }

    private fun initializeUI() {
        createMainContent()
        createToolbar()
        configureLayout()
    }

    private fun configureInitialState() {
        filtersPanel.isVisible = false
        filtersPanel.focusOnNewCodeCheckBox.isSelected =
            getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()
        filtersPanel.setStatusFilterVisible(Settings.getSettingsFor(project).isBound)
        showEmptyState()
    }
    
    private fun createFiltersPanel() = FiltersPanel(
        onFilterChanged = ::refreshFilteredView,
        onSortingChanged = ::handleSortingChange,
        onFocusOnNewCodeChanged = ::handleFocusOnNewCodeChange
    )
    
    private fun handleSortingChange(sortMode: SortMode) {
        with(treeManager) {
            issuesTreeBuilder.sortMode = sortMode
            oldIssuesTreeBuilder.sortMode = sortMode
            securityHotspotsTreeBuilder.sortMode = sortMode
            oldSecurityHotspotsTreeBuilder.sortMode = sortMode
            taintsTreeBuilder.sortMode = sortMode
            oldTaintsTreeBuilder.sortMode = sortMode
        }
        refreshFilteredView()
    }
    
    private fun handleFocusOnNewCodeChange(focusOnNewCode: Boolean) {
        runOnPooledThread(project) { 
            getService(CleanAsYouCodeService::class.java).setFocusOnNewCode(focusOnNewCode)
        }
    }
    
    private fun createMainContent() {
        val treePanel = JBPanel<ReportPanel>(VerticalFlowLayout(
            VERTICAL_FLOW_LAYOUT_HGAP,
            VERTICAL_FLOW_LAYOUT_VGAP
        )).apply {
            add(treeManager.issuesTree)
            add(treeManager.securityHotspotsTree)
            add(treeManager.taintsTree)
            add(treeManager.oldIssuesTree)
            add(treeManager.oldSecurityHotspotsTree)
            add(treeManager.oldTaintsTree)
        }
        
        findingsTreePane = ScrollPaneFactory.createScrollPane(treePanel, true)
        
        // Main findings panel
        findingsPanel = JBPanelWithEmptyText(BorderLayout()).apply {
            add(findingsTreePane, BorderLayout.CENTER)
            add(whatsNewPanel, BorderLayout.SOUTH)
        }
        
        // Header with filters
        val headerPanel = JBPanel<ReportPanel>(VerticalFlowLayout(0, 0)).apply {
            add(filtersPanel)
        }
        
        headerCardPanel = JBPanel<ReportPanel>(BorderLayout()).apply {
            background = UIUtil.getListBackground()
            putClientProperty("JComponent.roundRect", true)
            add(headerPanel, BorderLayout.CENTER)
        }
    }
    
    private fun createToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(ShowReportFiltersAction(this@ReportPanel))
            add(SonarLintActions.getInstance().configure())
            add(SonarLintActions.getInstance().clearReport())
        }
        
        mainToolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_ID, actionGroup, false)
        mainToolbar.targetComponent = this
        
        val toolBarBox = Box.createHorizontalBox().apply {
            add(mainToolbar.component)
        }
        
        super.setToolbar(toolBarBox)
        mainToolbar.component.isVisible = true
    }
    
    private fun configureLayout() {
        val mainContentPanel = JBPanel<ReportPanel>(BorderLayout()).apply {
            add(headerCardPanel, BorderLayout.NORTH)
            add(findingsPanel, BorderLayout.CENTER)
        }
        
        findingDetailsPanel.minimumSize = FINDING_DETAILS_MINIMUM_SIZE
        
        super.setContent(createSplitter(
            project, this, this, mainContentPanel, 
            findingDetailsPanel, SPLIT_PROPORTION_PROPERTY,
            DEFAULT_SPLIT_PROPORTION
        ))
    }
    
    private fun updateTreeModels(findings: LiveFindings, isFocusOnNewCode: Boolean) {
        val taints = filteredFindingsCache.taints
        val split = if (isFocusOnNewCode) {
            ReportFilteringUtils.splitFindingsByCodeAge(findings, taints)
        } else {
            ReportFilteringUtils.createNoFocusSplit(findings, taints)
        }
        
        runOnUiThread(project) {
            with(treeManager) {
                issuesTreeBuilder.updateModel(split.newIssues)
                oldIssuesTreeBuilder.updateModel(split.oldIssues)
                securityHotspotsTreeBuilder.updateModel(split.newHotspots)
                oldSecurityHotspotsTreeBuilder.updateModel(split.oldHotspots)
                taintsTreeBuilder.updateModel(split.newTaints)
                oldTaintsTreeBuilder.updateModel(split.oldTaints)
            }
        }
    }
    
    private fun refreshFilteredView() {
        lastAnalysisResult?.let { 
            applyFiltering()
            updateFindings(it)
        }
    }
    
    private fun applyFiltering() {
        val result = lastAnalysisResult
        if (result == null) {
            filteredFindingsCache = FilteredFindings(emptyList(), emptyList(), emptyList(), emptyList())
            return
        }
        
        detectAndUpdateMqrMode(result)
        val filterCriteria = displayManager.getCurrentFilterCriteria()
        filteredFindingsCache = findingsFilter.filterAllFindings(result, filterCriteria)
    }
    
    private fun detectAndUpdateMqrMode(analysisResult: AnalysisResult) {
        val rawFindings = analysisResult.findings
        val allIssues = rawFindings.issuesPerFile.values.flatten()
        val allHotspots = rawFindings.securityHotspotsPerFile.values.flatten()
        val allTaints = filteredFindingsCache.taints

        val rawFilteredFindings = FilteredFindings(allIssues, allHotspots, allTaints, emptyList())
        displayManager.updateMqrMode(rawFilteredFindings)
    }
    
    private fun showEmptyState() {
        runOnPooledThread(project, ::configureEmptyView)
        findingsTreePane.isVisible = false
        whatsNewPanel.isVisible = false
    }
    
    private fun showFindingsState() {
        findingsTreePane.isVisible = true
        whatsNewPanel.isVisible = true
    }
    
    private fun configureEmptyView() {
        val statusText = findingsPanel.emptyText
        val backendIsAlive = getService(BackendService::class.java).isAlive()
        
        if (!backendIsAlive) {
            statusText.text = RestartBackendAction.SONARLINT_ERROR_MSG
            statusText.appendLine("Restart SonarQube for IDE Service", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { 
                ActionUtil.invokeAction(restartSonarLintAction, this, TOOL_WINDOW_ID, null, null)
            }
        } else {
            configureAnalysisPrompts(statusText)
        }
    }
    
    private fun configureAnalysisPrompts(statusText: StatusText) {
        val sonarLintActions = SonarLintActions.getInstance()
        val analyzeChangedFiles = sonarLintActions.analyzeChangedFiles()
        val analyzeAllFiles = sonarLintActions.analyzeAllFiles()
        
        statusText.appendLine("")
        
        analyzeChangedFiles.templateText?.let { text ->
            statusText.appendText(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
                ActionUtil.invokeAction(analyzeChangedFiles, this, TOOL_WINDOW_ID, null, null)
            }
            if (analyzeAllFiles.templateText != null) {
                statusText.appendText(" or ")
            }
        }
        
        analyzeAllFiles.templateText?.let { text ->
            statusText.appendText(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
                ActionUtil.invokeAction(analyzeAllFiles, this, TOOL_WINDOW_ID, null, null)
            }
        }
    }

    override fun dispose() {
        lastAnalysisResult = null
    }

}
