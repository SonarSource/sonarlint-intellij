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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import org.sonarlint.intellij.actions.RestartBackendAction
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
    private var isLoadingState = false
    private var expectedModuleCount = 1
    private var receivedModuleCount = 0
    
    // Loading UI components
    private var loadingPanel: JBPanel<*>? = null
    private var loadingIcon: AsyncProcessIcon? = null
    private var loadingMainLabel: JBLabel? = null
    private var loadingProgressLabel: JBLabel? = null
    private var loadingStatusLabel: JBLabel? = null
    
    // Analysis status components (persistent during multi-module analysis)
    private var analysisStatusPanel: JBPanel<*>? = null
    private var analysisStatusIcon: AsyncProcessIcon? = null
    private var analysisStatusLabel: JBLabel? = null

    init {
        initializeUI()
        configureInitialState()
    }

    fun updateFindings(analysisResult: AnalysisResult) {
        if (project.isDisposed) return

        // Update progress if in loading state
        if (isLoadingState) {
            updateLoadingProgress()
        }

        lastAnalysisResult = analysisResult
        val isFocusOnNewCode = getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()

        applyFiltering()
        val findings = ReportFilteringUtils.convertFilteredFindingsToMap(filteredFindingsCache)

        updateTreeModels(findings, isFocusOnNewCode)
        treeManager.configureTreeVisibility(isFocusOnNewCode)
        treeManager.expandTrees()

        // Transition from loading to results
        if (isLoadingState) {
            hideLoadingPanel()
            isLoadingState = false
        }
        
        // Show/update ongoing analysis status if not all modules are complete
        if (expectedModuleCount > 1 && receivedModuleCount < expectedModuleCount) {
            showAnalysisStatusPanel()
        } else {
            hideAnalysisStatusPanel()
        }
        
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
    
    /**
     * Shows the loading state with a spinner and progress information.
     */
    fun showLoadingState(expectedModules: Int = 1) {
        isLoadingState = true
        expectedModuleCount = expectedModules
        receivedModuleCount = 0
        
        createLoadingPanel()
        showLoadingPanel()
    }
    
    /**
     * Updates the loading progress when a module completes analysis.
     */
    fun updateLoadingProgress() {
        receivedModuleCount++
        updateLoadingText()
        
        // Update analysis status panel if it exists
        if (analysisStatusPanel != null) {
            updateAnalysisStatusText()
        }
        
        // If all modules are complete, we'll transition to results when updateFindings is called
    }
    
    /**
     * Updates the analysis progress with explicit counts from the callback.
     */
    fun updateAnalysisProgress(completedModules: Int, expectedModules: Int) {
        receivedModuleCount = completedModules
        expectedModuleCount = expectedModules
        
        // Update loading text if in loading state
        if (isLoadingState) {
            updateLoadingText()
        }
        
        // Update analysis status panel if it exists
        if (analysisStatusPanel != null) {
            updateAnalysisStatusText()
        }
    }
    
    /**
     * Merges new analysis results with existing results and updates the display.
     * This is used for incremental updates as modules complete analysis.
     */
    fun mergeAnalysisResults(newAnalysisResult: AnalysisResult) {
        if (project.isDisposed) return
        
        lastAnalysisResult = lastAnalysisResult?.let { existing ->
            val mergedFindings = newAnalysisResult.findings.merge(existing.findings)
            val mergedFiles = (existing.analyzedFiles + newAnalysisResult.analyzedFiles).distinct()
            AnalysisResult(
                newAnalysisResult.analysisId,
                mergedFindings,
                mergedFiles,
                newAnalysisResult.analysisDate
            )
        } ?: newAnalysisResult
        
        // Update display with merged results
        updateFindings(lastAnalysisResult!!)
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
    
    private fun createToolbar() {
        val actions = listOf(
            SonarLintActions.getInstance().analyzeAllFiles(),
            SonarLintActions.getInstance().analyzeChangedFiles(),
            // Separator
            null,
            SonarLintActions.getInstance().expandAllTreesAction(),
            SonarLintActions.getInstance().collapseAllTreesAction(),
            null,
            SonarLintActions.getInstance().configure()
        )

        val actionGroup = DefaultActionGroup()
        actions.forEach { action ->
            if (action == null) {
                actionGroup.addSeparator()
            } else {
                actionGroup.add(action)
            }
        }
        
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOL_WINDOW_ID, actionGroup, false)
        toolbar.targetComponent = this
        
        val box = Box.createHorizontalBox()
        box.add(toolbar.component)
        setToolbar(box)
        toolbar.component.isVisible = true
    }
    
    private fun createFiltersPanel() = FiltersPanel(
        onFilterChanged = ::refreshFilteredView,
        onSortingChanged = ::handleSortingChange,
        onFocusOnNewCodeChanged = ::handleFocusOnNewCodeChange,
        onScopeModeChanged = { /* No action needed for Report tab */ },
        showScopeFilter = false // Hide scope filter in Report tab
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

        private fun createLoadingPanel() {
        loadingIcon = AsyncProcessIcon("SonarLint Analysis").apply {
            isVisible = true
        }

        loadingMainLabel = JBLabel("Analyzing files...").apply {
            font = font.deriveFont(16f)
            horizontalAlignment = SwingConstants.CENTER
        }

        loadingProgressLabel = JBLabel(getProgressText()).apply {
            font = font.deriveFont(13f)
            foreground = UIUtil.getContextHelpForeground()
            horizontalAlignment = SwingConstants.CENTER
        }

        loadingStatusLabel = JBLabel(getAnalysisStatusText()).apply {
            font = font.deriveFont(11f)
            foreground = UIUtil.getContextHelpForeground()
            horizontalAlignment = SwingConstants.CENTER
        }

        loadingPanel = JBPanel<ReportPanel>(BorderLayout()).apply {
            background = UIUtil.getPanelBackground()

            // Create a centered content panel
            val contentPanel = JBPanel<ReportPanel>(VerticalFlowLayout(0, 15)).apply {
                background = UIUtil.getPanelBackground()

                // Center the spinner horizontally
                val spinnerPanel = JBPanel<ReportPanel>(FlowLayout(FlowLayout.CENTER)).apply {
                    background = UIUtil.getPanelBackground()
                    add(loadingIcon!!)
                }

                add(spinnerPanel)
                add(loadingMainLabel!!)
                add(loadingProgressLabel!!)
                add(loadingStatusLabel!!)
            }

            // Center the content panel both horizontally and vertically
            val wrapperPanel = JBPanel<ReportPanel>(BorderLayout()).apply {
                background = UIUtil.getPanelBackground()
                add(contentPanel, BorderLayout.CENTER)
            }

            add(wrapperPanel, BorderLayout.CENTER)
        }
    }
    
    private fun showLoadingPanel() {
        loadingPanel?.let { panel ->
            findingsPanel.removeAll()
            findingsPanel.add(panel, BorderLayout.CENTER)
            findingsPanel.revalidate()
            findingsPanel.repaint()
        }
    }
    
    private fun hideLoadingPanel() {
        // Dispose and clear all loading references
        loadingIcon?.dispose()
        loadingIcon = null
        loadingPanel = null
        loadingMainLabel = null
        loadingProgressLabel = null
        loadingStatusLabel = null
        
        // Restore original content
        findingsPanel.removeAll()
        findingsPanel.add(findingsTreePane, BorderLayout.CENTER)
        findingsPanel.add(whatsNewPanel, BorderLayout.SOUTH)
        findingsPanel.revalidate()
        findingsPanel.repaint()
    }
    
    private fun updateLoadingText() {
        loadingProgressLabel?.text = getProgressText()
        loadingStatusLabel?.text = getAnalysisStatusText()
        
        // Force repaint
        loadingPanel?.let { panel ->
            panel.revalidate()
            panel.repaint()
        }
    }
    
    private fun getProgressText(): String {
        return if (expectedModuleCount > 1) {
            "Analyzing modules ($receivedModuleCount/$expectedModuleCount completed)..."
        } else {
            "Analyzing files..."
        }
    }
    
    private fun getAnalysisStatusText(): String {
        return if (expectedModuleCount > 1 && receivedModuleCount < expectedModuleCount) {
            val remaining = expectedModuleCount - receivedModuleCount
            "Analysis ongoing • $remaining ${if (remaining == 1) "module" else "modules"} remaining"
        } else if (expectedModuleCount > 1) {
            "Finalizing results..."
        } else {
            "Preparing analysis..."
        }
    }

    private fun createAnalysisStatusPanel() {
        analysisStatusIcon = AsyncProcessIcon("Analysis Status").apply {
            isVisible = true
        }
        
        analysisStatusLabel = JBLabel(getAnalysisStatusText()).apply {
            font = font.deriveFont(12f)
            foreground = UIUtil.getContextHelpForeground()
        }
        
        analysisStatusPanel = JBPanel<ReportPanel>(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            background = UIUtil.getListBackground()
            add(analysisStatusIcon!!)
            add(analysisStatusLabel!!)
        }
    }
    
    private fun showAnalysisStatusPanel() {
        if (analysisStatusPanel == null) {
            createAnalysisStatusPanel()
        }
        
        analysisStatusPanel?.let { panel ->
            // Add to header if not already present
            if (!headerCardPanel.isAncestorOf(panel)) {
                val headerPanel = headerCardPanel.components.filterIsInstance<JBPanel<*>>().firstOrNull()
                headerPanel?.add(panel, 0) // Add at the top
                headerCardPanel.revalidate()
                headerCardPanel.repaint()
            }
            
            // Always update the status text when showing
            updateAnalysisStatusText()
        }
    }
    
    private fun hideAnalysisStatusPanel() {
        analysisStatusPanel?.let { panel ->
            val headerPanel = headerCardPanel.components.filterIsInstance<JBPanel<*>>().firstOrNull()
            headerPanel?.remove(panel)
            headerCardPanel.revalidate()
            headerCardPanel.repaint()
        }
        
        // Dispose and clear references
        analysisStatusIcon?.dispose()
        analysisStatusIcon = null
        analysisStatusPanel = null
        analysisStatusLabel = null
    }
    
    private fun updateAnalysisStatusText() {
        analysisStatusLabel?.text = getAnalysisStatusText()
        
        // Force repaint to ensure the text update is visible
        analysisStatusPanel?.let { panel ->
            panel.revalidate()
            panel.repaint()
        }
    }

    fun expandAllTrees() {
        treeManager.expandAllTrees()
    }
    
    fun collapseAllTrees() {
        treeManager.collapseTrees()
    }

    override fun dispose() {
        loadingIcon?.dispose()
        analysisStatusIcon?.dispose()
        
        // Clear all references
        loadingPanel = null
        loadingIcon = null
        loadingMainLabel = null
        loadingProgressLabel = null
        loadingStatusLabel = null
        
        analysisStatusPanel = null
        analysisStatusIcon = null
        analysisStatusLabel = null
        
        lastAnalysisResult = null
    }

}
