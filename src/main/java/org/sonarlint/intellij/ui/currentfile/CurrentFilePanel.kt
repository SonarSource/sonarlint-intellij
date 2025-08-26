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
package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JScrollPane
import javax.swing.tree.TreePath
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.messages.ProjectConfigurationListener
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.currentfile.filter.CurrentFileFindingsFilter
import org.sonarlint.intellij.ui.currentfile.filter.FilteredFindings
import org.sonarlint.intellij.ui.currentfile.filter.FiltersPanel
import org.sonarlint.intellij.ui.currentfile.filter.SortMode
import org.sonarlint.intellij.ui.currentfile.filter.StatusFilter
import org.sonarlint.intellij.ui.currentfile.tree.SingleFileTreeModelBuilder
import org.sonarlint.intellij.ui.factory.PanelFactory
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.util.SonarLintActions
import org.sonarlint.intellij.util.runOnPooledThread

/**
 * Support status for a finding type
 */
sealed class FindingSupportStatus {
    object Supported : FindingSupportStatus()
    data class NotSupported(val reason: String) : FindingSupportStatus()
    object CheckingSupport : FindingSupportStatus()
    object NotBound : FindingSupportStatus()
}

/**
 * Main panel component for the Current File tab in SonarLint tool window.
 * 
 * <h3>Design & Architecture:</h3>
 * This panel serves as the primary orchestrator for displaying findings in the currently opened file.
 * It implements a multi-layered architecture that handles filtering, display management,
 * and tree organization for different types of findings.
 * 
 * <h3>Key Components Integration:</h3>
 * The panel coordinates multiple specialized components:
 * - {@link CurrentFileSummaryPanel}:</strong> Header with finding counts and collapse/expand controls
 * - {@link FiltersPanel}:</strong> Filtering and sorting controls (search, severity, status, etc.)
 * - {@link CurrentFileDisplayManager}:</strong> Manages display state, MQR mode, and UI updates
 * - {@link CurrentFileFindingsFilter}:</strong> Applies filtering logic to findings
 * - Tree Components:</strong> Multiple {@link SingleFileTreeModelBuilder} implementations for each finding type
 * - FindingDetailsPanel:</strong> Shows detailed information for selected findings
 */
class CurrentFilePanel(project: Project) : CurrentFileFindingsPanel(project) {

    // UI Components
    private lateinit var issuesPanel: JBPanelWithEmptyText
    private lateinit var treeScrollPane: JScrollPane
    private var summaryPanel: CurrentFileSummaryPanel
    private var filtersPanel: FiltersPanel

    // Actions
    private val analyzeCurrentFileAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
    private val restartSonarLintAction = SonarLintActions.getInstance().restartSonarLintAction()

    private var findingsFilter: CurrentFileFindingsFilter = CurrentFileFindingsFilter(project)
    private var displayManager: CurrentFileDisplayManager

    // Support status tracking
    private var hotspotSupportStatus: FindingSupportStatus = FindingSupportStatus.CheckingSupport
    private var taintSupportStatus: FindingSupportStatus = FindingSupportStatus.CheckingSupport  
    private var dependencyRiskSupportStatus: FindingSupportStatus = FindingSupportStatus.CheckingSupport

    private var filteredFindingsCache = FilteredFindings(listOf(), listOf(), listOf(), listOf())

    init {
        filtersPanel = FiltersPanel(
            { refreshView() },
            { sortMode -> treeConfigs.values.forEach { it.builder.setSortMode(SortMode.valueOf(sortMode.name)) } },
            { focusOnNewCode ->
                runOnPooledThread(project) {
                    getService(CleanAsYouCodeService::class.java).setFocusOnNewCode(focusOnNewCode)
                }
            }
        )

        summaryPanel = CurrentFileSummaryPanel(
            createSummaryToggleHandler(TreeType.ISSUES),
            createSummaryToggleHandler(TreeType.HOTSPOTS),
            createSummaryToggleHandler(TreeType.TAINTS),
            createSummaryToggleHandler(TreeType.DEPENDENCY_RISKS)
        ) { selected ->
            filtersPanel.isVisible = selected
        }

        // Set up UI layout first
        setupLayout()
        
        // Initialize display manager after UI components are ready
        displayManager = CurrentFileDisplayManager(
            project, filtersPanel
        )
        
        // Initialize checkbox state
        filtersPanel.focusOnNewCodeCheckBox.isSelected = getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()
        
        // Initialize status filter visibility based on connected mode
        filtersPanel.setStatusFilterVisible(Settings.getSettingsFor(project).isBound)

        // Check support status for connected-mode features
        checkSupportStatus()

        // Set up toolbar and listeners
        setupToolbar()
        setupStatusListener()
    }

    private fun checkSupportStatus() {
        val isBound = Settings.getSettingsFor(project).isBound
        
        // Update status filter visibility based on binding status
        filtersPanel.setStatusFilterVisible(isBound)
        
        if (!isBound) {
            // User is not in connected mode - all connected features are not available
            hotspotSupportStatus = FindingSupportStatus.NotBound
            taintSupportStatus = FindingSupportStatus.NotBound
            dependencyRiskSupportStatus = FindingSupportStatus.NotBound
            return
        }

        // Check hotspot support
        runOnPooledThread(project) {
            try {
                getService(BackendService::class.java)
                    .checkLocalSecurityHotspotDetectionSupported(project)
                    .thenAcceptAsync { response ->
                        runOnUiThread(project) {
                            hotspotSupportStatus = if (response.isSupported) {
                                FindingSupportStatus.Supported
                            } else {
                                FindingSupportStatus.NotSupported(response.reason!!)
                            }
                            updateSummaryButtons()
                        }
                    }
            } catch (e: Exception) {
                runOnUiThread(project) {
                    hotspotSupportStatus = FindingSupportStatus.NotSupported("Error checking support: ${e.message}")
                    updateSummaryButtons()
                }
            }
        }

        // Check dependency risk support  
        runOnPooledThread(project) {
            try {
                getService(BackendService::class.java)
                    .checkIfDependencyRiskSupported(project)
                    .thenAcceptAsync { response ->
                        runOnUiThread(project) {
                            dependencyRiskSupportStatus = if (response.isSupported) {
                                FindingSupportStatus.Supported
                            } else {
                                FindingSupportStatus.NotSupported(response.reason!!)
                            }
                            updateSummaryButtons()
                        }
                    }
            } catch (e: Exception) {
                runOnUiThread(project) {
                    dependencyRiskSupportStatus = FindingSupportStatus.NotSupported("Error checking support: ${e.message}")
                    updateSummaryButtons()
                }
            }
        }

        taintSupportStatus = FindingSupportStatus.Supported
    }

    private fun isFeatureSupported(treeType: TreeType): Boolean {
        return when (treeType) {
            TreeType.ISSUES -> true // Issues are always supported
            TreeType.HOTSPOTS -> hotspotSupportStatus is FindingSupportStatus.Supported
            TreeType.TAINTS -> taintSupportStatus is FindingSupportStatus.Supported  
            TreeType.DEPENDENCY_RISKS -> dependencyRiskSupportStatus is FindingSupportStatus.Supported
        }
    }

    private fun getFeatureSupportReason(treeType: TreeType): String? {
        return when (treeType) {
            TreeType.ISSUES -> null
            TreeType.HOTSPOTS -> when (val status = hotspotSupportStatus) {
                is FindingSupportStatus.NotSupported -> status.reason
                is FindingSupportStatus.NotBound -> "Connect to SonarQube or SonarCloud to enable Security Hotspots"
                is FindingSupportStatus.CheckingSupport -> "Checking support..."
                else -> null
            }
            TreeType.TAINTS -> when (val status = taintSupportStatus) {
                is FindingSupportStatus.NotSupported -> status.reason
                is FindingSupportStatus.NotBound -> "Connect to SonarQube or SonarCloud to enable Taint Vulnerabilities"
                is FindingSupportStatus.CheckingSupport -> "Checking support..."
                else -> null
            }
            TreeType.DEPENDENCY_RISKS -> when (val status = dependencyRiskSupportStatus) {
                is FindingSupportStatus.NotSupported -> status.reason
                is FindingSupportStatus.NotBound -> "Connect to SonarQube or SonarCloud to enable Dependency Risks"
                is FindingSupportStatus.CheckingSupport -> "Checking support..."
                else -> null
            }
        }
    }

    private fun setupLayout() {
        val treePanel = JBPanel<CurrentFilePanel>(VerticalFlowLayout(0, 0)).apply {
            treeConfigs.values.forEach { add(it.tree) }
        }

        setUpTreeListeners()
        treeScrollPane = ScrollPaneFactory.createScrollPane(treePanel, true)

        issuesPanel = JBPanelWithEmptyText(BorderLayout()).apply {
            background = com.intellij.util.ui.UIUtil.getPanelBackground()
            border = JBUI.Borders.empty(4, 8)
        }
        issuesPanel.add(treeScrollPane, BorderLayout.CENTER)
        disableEmptyDisplay()

        // Header with summary and filters
        val headerPanel = JBPanel<CurrentFilePanel>(VerticalFlowLayout(0, 0)).apply {
            add(summaryPanel)
            add(filtersPanel)
        }

        val headerCardPanel = JBPanel<CurrentFilePanel>(BorderLayout()).apply {
            background = com.intellij.util.ui.UIUtil.getListBackground()
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.DARK_GRAY, 0, 0, 1, 0),
                JBUI.Borders.empty(6, 8)
            )
            putClientProperty("JComponent.roundRect", true)
            add(headerPanel, BorderLayout.CENTER)
        }

        val verticalContainer = JBPanel<CurrentFilePanel>(BorderLayout()).apply {
            add(headerCardPanel, BorderLayout.NORTH)
            add(issuesPanel, BorderLayout.CENTER)
        }

        val mainPanel = JBPanel<CurrentFilePanel>(BorderLayout()).apply {
            add(verticalContainer, BorderLayout.CENTER)
            add(CurrentFileStatusPanel(project), BorderLayout.PAGE_END)
        }

        findingDetailsPanel.minimumSize = Dimension(350, 200)
        val splitter = PanelFactory.createSplitter(
            project, this, this, mainPanel, findingDetailsPanel,
            "SONARLINT_ISSUES_SPLIT_PROPORTION", 0.5f
        )

        super.setContent(splitter)
    }

    private fun setupToolbar() {
        setToolbar(listOf(
            SonarLintActions.getInstance().analyzeCurrentFileAction(),
            SonarLintActions.getInstance().analyzeChangedFiles(),
            SonarLintActions.getInstance().analyzeAllFiles(),
            SonarLintActions.getInstance().cancelAnalysis(),
            SonarLintActions.getInstance().configure(),
            SonarLintActions.getInstance().clearIssues()
        ))
    }

    private fun setupStatusListener() {
        val busConnection = project.messageBus.connect()
        with(busConnection) {
            subscribe(ProjectConfigurationListener.TOPIC, ProjectConfigurationListener { checkSupportStatus() })
            subscribe(GlobalConfigurationListener.TOPIC, object : GlobalConfigurationListener.Adapter() {
                override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
                    checkSupportStatus()
                }
            })
        }
    }

    fun update(file: VirtualFile?) {
        this.currentFile = file

        // Early returns for invalid states
        if (!handleBackendAlive()) return

        // Filtering
        val filterCriteria = displayManager.getCurrentFilterCriteria()
        filteredFindingsCache = findingsFilter.filterAllFindings(file, filterCriteria)

        // Update UI using the display manager
        displayManager.updateMqrMode(filteredFindingsCache)
        displayManager.updateIcons(filteredFindingsCache)

        // Populate trees
        populateTreesWithNewCodeFilter(TreeType.ISSUES, filteredFindingsCache.issues) { !summaryPanel.areIssuesEnabled() }

        // Populate connected-mode trees based on support status
        if (isFeatureSupported(TreeType.HOTSPOTS)) {
            populateTreesWithNewCodeFilter(TreeType.HOTSPOTS, filteredFindingsCache.hotspots) { !summaryPanel.areHotspotsEnabled() }
        } else {
            getBuilder<LiveSecurityHotspot>(TreeType.HOTSPOTS, isOld = false).updateModel(currentFile, listOf())
            getBuilder<LiveSecurityHotspot>(TreeType.HOTSPOTS, isOld = true).updateModel(currentFile, listOf())
            getTree(TreeType.HOTSPOTS, isOld = false).isVisible = false
            getTree(TreeType.HOTSPOTS, isOld = true).isVisible = false
        }
        
        if (isFeatureSupported(TreeType.TAINTS)) {
            populateTreesWithNewCodeFilter(TreeType.TAINTS, filteredFindingsCache.taints) { !summaryPanel.areTaintsEnabled() }
        } else {
            getBuilder<LocalTaintVulnerability>(TreeType.TAINTS, isOld = false).updateModel(currentFile, listOf())
            getBuilder<LocalTaintVulnerability>(TreeType.TAINTS, isOld = true).updateModel(currentFile, listOf())
            getTree(TreeType.TAINTS, isOld = false).isVisible = false
            getTree(TreeType.TAINTS, isOld = true).isVisible = false
        }
        
        if (isFeatureSupported(TreeType.DEPENDENCY_RISKS)) {
            populateTreesWithNewCodeFilter(TreeType.DEPENDENCY_RISKS, filteredFindingsCache.dependencyRisks) { !summaryPanel.areDependencyRisksEnabled() }
        } else {
            getBuilder<LocalDependencyRisk>(TreeType.DEPENDENCY_RISKS, isOld = false).updateModel(currentFile, listOf())
            getBuilder<LocalDependencyRisk>(TreeType.DEPENDENCY_RISKS, isOld = true).updateModel(currentFile, listOf())
            getTree(TreeType.DEPENDENCY_RISKS, isOld = false).isVisible = false
            getTree(TreeType.DEPENDENCY_RISKS, isOld = true).isVisible = false
        }
        
        // Handle display status and expand trees
        handleDisplayStatus()
        expandTrees()
        updateSummaryButtons()
    }

    private fun handleBackendAlive(): Boolean {
        val backendIsAlive = getService(BackendService::class.java).isAlive()
        if (!backendIsAlive) {
            showBackendErrorMessage()
            return false
        }
        return true
    }

    private fun showBackendErrorMessage() {
        val statusText = issuesPanel.emptyText
        statusText.text = RestartBackendAction.SONARLINT_ERROR_MSG
        statusText.appendLine("Restart SonarQube for IDE Service", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
            ActionUtil.invokeAction(restartSonarLintAction, this, TOOL_WINDOW_ID, null, null)
        }
        enableEmptyDisplay()
        clearAllTrees()
    }

    private fun clearAllTrees() {
        treeConfigs.values.forEach { config ->
            config.builder.updateModel(currentFile, listOf())
            config.tree.showsRootHandles = false
            config.tree.isVisible = false  // Hide empty trees
        }
    }

    private fun createSummaryToggleHandler(treeType: TreeType): (Boolean) -> Unit = { selected ->
        val tree = getTree(treeType, isOld = false)
        val oldTree = getTree(treeType, isOld = true)
        val builder = getBuilder<Finding>(treeType, isOld = false)
        val oldBuilder = getBuilder<Finding>(treeType, isOld = true)
        
        // Only show tree if not collapsed AND has displayed findings (after filtering)
        tree.isVisible = !selected && builder.numberOfDisplayedFindings() > 0
        if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()) {
            oldTree.isVisible = !selected && oldBuilder.numberOfDisplayedFindings() > 0
        }
        
        // Update display status to show proper empty messages when trees are hidden
        runOnUiThread(project) { handleDisplayStatus() }
    }

    fun refreshView() {
        runOnUiThread(project) { this.update(currentFile) }
    }

    private fun setUpTreeListeners() {
        treeConfigs.values.forEach { config ->
            config.tree.addTreeSelectionListener {
                if (!config.tree.isSelectionEmpty) {
                    treeConfigs.values.forEach { otherTreeConfig ->
                        if (otherTreeConfig.tree != config.tree) {
                            otherTreeConfig.tree.clearSelection()
                        }
                    }
                    updateDetailsPanelForFinding(config.tree)
                }
            }
        }
    }

    fun getIssueFiltered(issueKey: String): LiveIssue? {
        return getBuilder<LiveIssue>(TreeType.ISSUES, isOld = false).findFindingByKey(issueKey)
            ?: getBuilder<LiveIssue>(TreeType.ISSUES, isOld = true).findFindingByKey(issueKey)
    }

    fun doesIssueExist(issueKey: String): Boolean {
        return getBuilder<LiveIssue>(TreeType.ISSUES, isOld = false).findFindingByKey(issueKey) != null
            || getBuilder<LiveIssue>(TreeType.ISSUES, isOld = true).findFindingByKey(issueKey) != null
    }

    fun getTaintFiltered(taintKey: String): LocalTaintVulnerability? {
        return getBuilder<LocalTaintVulnerability>(TreeType.TAINTS, isOld = false).findFindingByKey(taintKey)
            ?: getBuilder<LocalTaintVulnerability>(TreeType.TAINTS, isOld = true).findFindingByKey(taintKey)
    }

    fun doesTaintExist(taintKey: String): Boolean {
        return getBuilder<LocalTaintVulnerability>(TreeType.TAINTS, isOld = false).findFindingByKey(taintKey) != null
            || getBuilder<LocalTaintVulnerability>(TreeType.TAINTS, isOld = true).findFindingByKey(taintKey) != null
    }

    fun getHotspotFiltered(hotspotKey: String): LiveSecurityHotspot? {
        return getBuilder<LiveSecurityHotspot>(TreeType.HOTSPOTS, isOld = false).findFindingByKey(hotspotKey)
            ?: getBuilder<LiveSecurityHotspot>(TreeType.HOTSPOTS, isOld = true).findFindingByKey(hotspotKey)
    }

    fun doesHotspotExist(hotspotKey: String): Boolean {
        return getBuilder<LiveSecurityHotspot>(TreeType.HOTSPOTS, isOld = false).findFindingByKey(hotspotKey) != null
            || getBuilder<LiveSecurityHotspot>(TreeType.HOTSPOTS, isOld = true).findFindingByKey(hotspotKey) != null
    }

    private fun enableEmptyDisplay() {
        treeScrollPane.isVisible = false
    }

    private fun disableEmptyDisplay() {
        treeScrollPane.isVisible = true
    }

    private fun updateDetailsPanelForFinding(tree: Tree) {
        when (val selected = tree.lastSelectedPathComponent) {
            is IssueNode -> findingDetailsPanel.show(selected.issue(), false)
            is LiveSecurityHotspotNode -> findingDetailsPanel.show(selected.hotspot, false)
            is LocalTaintVulnerability -> findingDetailsPanel.show(selected, false)
            else -> findingDetailsPanel.clear()
        }
    }

    private fun <T : Finding> populateTreesWithNewCodeFilter(treeType: TreeType, findings: List<T>, treeVisibilityCheck: (() -> Boolean)? = null) {
        val tree = getTree(treeType, false)
        val oldTree = getTree(treeType, true)
        val treeBuilder = getBuilder<T>(treeType, false)
        val oldTreeBuilder = getBuilder<T>(treeType, true)
        
        if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()) {
            val newFindings = findings.filter { it.isOnNewCode() }
            val oldFindings = findings.filter { !it.isOnNewCode() }
            populateSubTree(tree, treeBuilder, newFindings)
            populateSubTree(oldTree, oldTreeBuilder, oldFindings)
            
            // Show tree only if summary panel allows it AND it has displayed findings (after filtering)
            val summaryAllowsTree = treeVisibilityCheck?.invoke() ?: true
            oldTree.isVisible = summaryAllowsTree && oldTreeBuilder.numberOfDisplayedFindings() > 0
        } else {
            populateSubTree(tree, treeBuilder, findings)
            populateSubTree(oldTree, oldTreeBuilder, listOf())
            oldTree.isVisible = false
        }
        
        // Show tree only if summary panel allows it AND it has displayed findings (after filtering)
        val summaryAllowsTree = treeVisibilityCheck?.invoke() ?: true
        tree.isVisible = summaryAllowsTree && treeBuilder.numberOfDisplayedFindings() > 0
        
        tree.showsRootHandles = findings.isNotEmpty()
        oldTree.showsRootHandles = findings.isNotEmpty()
    }

    private fun <T : Finding> populateSubTree(tree: Tree, treeBuilder: SingleFileTreeModelBuilder<T>, issues: List<T>) {
        treeBuilder.updateModel(currentFile, issues)
        tree.showsRootHandles = issues.isNotEmpty()
    }

    private fun handleDisplayStatus() {
        val emptyText = issuesPanel.emptyText
        if (getService(AnalysisReadinessCache::class.java).isProjectReady) {
            setAnalysisIsReady()
        } else {
            emptyText.text = "Waiting for SonarQube for IDE to be ready"
            enableEmptyDisplay()
        }

        val hasActiveFindings = hasAnyFindingsInActiveTypes()
        val hasDisplayedFindings = isDisplayingFindings()
        
        if (!hasActiveFindings) {
            emptyText.text = "No findings to display"
            enableEmptyDisplay()
        } else if (!hasDisplayedFindings) {
            emptyText.text = "No findings displayed due to current filtering"
            enableEmptyDisplay()
        } else {
            disableEmptyDisplay()
        }
    }

    private fun hasAnyFindingsInActiveTypes(): Boolean {
        // Check each type that is currently visible (not collapsed by summary panel)
        // areXxxEnabled() returns true when the button is selected/pressed (tree is collapsed)
        val hasIssues = !summaryPanel.areIssuesEnabled() && 
            (!getBuilder<Finding>(TreeType.ISSUES, isOld = false).isEmpty() || 
             !getBuilder<Finding>(TreeType.ISSUES, isOld = true).isEmpty())
        
        val hasHotspots = !summaryPanel.areHotspotsEnabled() && 
            (!getBuilder<Finding>(TreeType.HOTSPOTS, isOld = false).isEmpty() || 
             !getBuilder<Finding>(TreeType.HOTSPOTS, isOld = true).isEmpty())
        
        val hasTaints = !summaryPanel.areTaintsEnabled() && 
            (!getBuilder<Finding>(TreeType.TAINTS, isOld = false).isEmpty() || 
             !getBuilder<Finding>(TreeType.TAINTS, isOld = true).isEmpty())
        
        val hasDependencyRisks = !summaryPanel.areDependencyRisksEnabled() && 
            (!getBuilder<Finding>(TreeType.DEPENDENCY_RISKS, isOld = false).isEmpty() || 
             !getBuilder<Finding>(TreeType.DEPENDENCY_RISKS, isOld = true).isEmpty())
        
        return hasIssues || hasHotspots || hasTaints || hasDependencyRisks
    }

    private fun isDisplayingFindings(): Boolean {
        return (!summaryPanel.areIssuesEnabled() && (getBuilder<Finding>(TreeType.ISSUES, isOld = false).numberOfDisplayedFindings() > 0 || getBuilder<Finding>(TreeType.ISSUES, isOld = true).numberOfDisplayedFindings() > 0))
            || (!summaryPanel.areHotspotsEnabled() && (getBuilder<Finding>(TreeType.HOTSPOTS, isOld = false).numberOfDisplayedFindings() > 0 || getBuilder<Finding>(TreeType.HOTSPOTS, isOld = true).numberOfDisplayedFindings() > 0))
            || (!summaryPanel.areTaintsEnabled() && (getBuilder<Finding>(TreeType.TAINTS, isOld = false).numberOfDisplayedFindings() > 0 || getBuilder<Finding>(TreeType.TAINTS, isOld = true).numberOfDisplayedFindings() > 0))
            || (!summaryPanel.areDependencyRisksEnabled() && (getBuilder<Finding>(TreeType.DEPENDENCY_RISKS, isOld = false).numberOfDisplayedFindings() > 0 || getBuilder<Finding>(TreeType.DEPENDENCY_RISKS, isOld = true).numberOfDisplayedFindings() > 0))
    }

    private fun updateSummaryButtons() {
        val issueSummaryUiModel = getBuilder<Finding>(TreeType.ISSUES, isOld = false).getSummaryUiModel()
        val hotspotSummaryUiModel = getBuilder<Finding>(TreeType.HOTSPOTS, isOld = false).getSummaryUiModel()
        val taintSummaryUiModel = getBuilder<Finding>(TreeType.TAINTS, isOld = false).getSummaryUiModel()
        val dependencyRiskSummaryUiModel = getBuilder<Finding>(TreeType.DEPENDENCY_RISKS, isOld = false).getSummaryUiModel()

        summaryPanel.updateIssues(getBuilder<Finding>(TreeType.ISSUES, isOld = false).numberOfDisplayedFindings(), issueSummaryUiModel)
        summaryPanel.updateHotspots(getBuilder<Finding>(TreeType.HOTSPOTS, isOld = false).numberOfDisplayedFindings(), hotspotSummaryUiModel)
        summaryPanel.updateTaints(getBuilder<Finding>(TreeType.TAINTS, isOld = false).numberOfDisplayedFindings(), taintSummaryUiModel)
        summaryPanel.updateDependencyRisks(getBuilder<Finding>(TreeType.DEPENDENCY_RISKS, isOld = false).numberOfDisplayedFindings(), dependencyRiskSummaryUiModel)

        // Use support status instead of just binding status
        summaryPanel.setHotspotsEnabled(isFeatureSupported(TreeType.HOTSPOTS))
        summaryPanel.setTaintsEnabled(isFeatureSupported(TreeType.TAINTS))
        summaryPanel.setDependencyRisksEnabled(isFeatureSupported(TreeType.DEPENDENCY_RISKS))
        
        // Set tooltips with support reasons for unsupported features
        getFeatureSupportReason(TreeType.HOTSPOTS)?.let { reason ->
            summaryPanel.setHotspotsTooltip(reason)
        }
        getFeatureSupportReason(TreeType.TAINTS)?.let { reason ->
            summaryPanel.setTaintsTooltip(reason)
        }
        getFeatureSupportReason(TreeType.DEPENDENCY_RISKS)?.let { reason ->
            summaryPanel.setDependencyRisksTooltip(reason)
        }

        runOnUiThread(project) { handleDisplayStatus() }
    }

    private fun expandTrees() {
        TreeUtil.expandAll(getTree(TreeType.ISSUES, isOld = false))
        TreeUtil.expandAll(getTree(TreeType.HOTSPOTS, isOld = false))
        TreeUtil.expandAll(getTree(TreeType.DEPENDENCY_RISKS, isOld = false))
        
        // For taint trees, expand only file nodes (not the full tree as it can be very deep)
        expandTaintFileNodes(getTree(TreeType.TAINTS, isOld = false))
    }
    
    private fun expandTaintFileNodes(tree: Tree) {
        val root = tree.model.root
        if (root != null) {
            tree.expandPath(TreePath(root))

            for (i in 0 until tree.model.getChildCount(root)) {
                val fileNode = tree.model.getChild(root, i)
                tree.expandPath(TreePath(arrayOf(root, fileNode)))
            }
        }
    }

    fun trySelectIssueForCodeFix(issue: LiveIssue) {
        selectAndOpenCodeFixTab(issue)
    }

    fun trySelectTaintForCodeFix(taint: LocalTaintVulnerability) {
        findingDetailsPanel.show(taint, true)
    }

    fun <T : Finding> trySelectFilteredIssue(issue: LiveIssue?, showFinding: ShowFinding<T>) {
        updateOnSelect(issue, showFinding)
    }

    fun <T : Finding> trySelectFilteredTaint(taint: LocalTaintVulnerability?, showFinding: ShowFinding<T>) {
        if (taint != null) {
            findingDetailsPanel.show(taint, false)
        } else {
            updateOnSelect(null, showFinding)
        }
    }

    fun <T : Finding> trySelectFilteredHotspot(hotspot: LiveSecurityHotspot?, showFinding: ShowFinding<T>) {
        updateOnSelect(hotspot, showFinding)
    }

    fun setAnalysisIsReady() {
        val emptyText = issuesPanel.emptyText
        val templateText = analyzeCurrentFileAction.templateText
        emptyText.text = "No analysis done on the current opened file"
        if (templateText != null) {
            emptyText.appendLine(templateText, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
                ActionUtil.invokeAction(
                    analyzeCurrentFileAction,
                    this,
                    TOOL_WINDOW_ID,
                    null,
                    null
                )
            }
        }
    }

    fun getDisplayedFindings(): FilteredFindings {
        return filteredFindingsCache
    }

    fun allowResolvedFindings(isResolved: Boolean) {
        val newStatus = if (isResolved) StatusFilter.NO_FILTER else StatusFilter.OPEN
        filtersPanel.filterStatus = newStatus
        filtersPanel.statusCombo.selectedItem = newStatus
        refreshView()
    }

}
