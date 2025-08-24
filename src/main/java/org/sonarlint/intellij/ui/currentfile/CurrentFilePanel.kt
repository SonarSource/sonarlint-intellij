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

import com.intellij.openapi.actionSystem.ActionManager
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
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.messages.StatusListener
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.factory.PanelFactory
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.util.SonarLintActions
import org.sonarlint.intellij.util.runOnPooledThread

/**
 * Main panel component for the Current File tab in SonarLint tool window.
 * 
 * <h3>Design & Architecture:</h3>
 * <p>This panel serves as the primary orchestrator for displaying findings in the currently opened file.
 * It implements a multi-layered architecture that handles filtering, display management,
 * and tree organization for different types of findings.</p>
 * 
 * <h3>Key Components Integration:</h3>
 * <p>The panel coordinates multiple specialized components:</p>
 * <ul>
 *   <li><strong>{@link CurrentFileSummaryPanel}:</strong> Header with finding counts and collapse/expand controls</li>
 *   <li><strong>{@link FiltersPanel}:</strong> Filtering and sorting controls (search, severity, status, etc.)</li>
 *   <li><strong>{@link CurrentFileDisplayManager}:</strong> Manages display state, MQR mode, and UI updates</li>
 *   <li><strong>{@link CurrentFileFindingsFilter}:</strong> Applies filtering logic to findings</li>
 *   <li><strong>Tree Components:</strong> Multiple {@link SingleFileTreeModelBuilder} implementations for each finding type</li>
 *   <li><strong>FindingDetailsPanel:</strong> Shows detailed information for selected findings</li>
 * </ul>
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

        // Set up toolbar and listeners
        setupToolbar()
        setupStatusListener()
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
            ActionManager.getInstance().getAction("SonarLint.toolwindow.Cancel"),
            ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"),
            SonarLintActions.getInstance().clearIssues()
        ))
    }

    private fun setupStatusListener() {
        project.messageBus.connect().subscribe(
            StatusListener.SONARLINT_STATUS_TOPIC,
            StatusListener { _ -> runOnUiThread(project) { this.refreshToolbar() } }
        )
    }

    fun update(file: VirtualFile?) {
        this.currentFile = file
        
        // Early returns for invalid states
        if (!handleBackendAlive()) return
        if (!handleFileOpen(file)) return

        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        val issues = onTheFlyFindingsHolder.getIssuesForFile(file!!).toList()
        displayManager.updateMqrMode(issues)

        // Filtering
        val filterCriteria = displayManager.getCurrentFilterCriteria()
        val filteredFindings = findingsFilter.filterAllFindings(file, filterCriteria)

        // Update UI using the display manager
        displayManager.updateIcons(file, filteredFindings)
        
        // Populate trees
        populateTreesWithNewCodeFilter(TreeType.ISSUES, filteredFindings.issues) { !summaryPanel.areIssuesEnabled() }
        
        val isBound = Settings.getSettingsFor(project).isBound
        filtersPanel.setStatusFilterVisible(isBound)
        
        if (isBound) {
            populateTreesWithNewCodeFilter(TreeType.HOTSPOTS, filteredFindings.hotspots) { !summaryPanel.areHotspotsEnabled() }
            populateTreesWithNewCodeFilter(TreeType.TAINTS, filteredFindings.taints) { !summaryPanel.areTaintsEnabled() }
            populateTreesWithNewCodeFilter(TreeType.DEPENDENCY_RISKS, filteredFindings.dependencyRisks) { !summaryPanel.areDependencyRisksEnabled() }
        } else {
            treeConfigs.entries.filter { it.key.first != TreeType.ISSUES }.forEach {
                it.value.builder.updateModel(currentFile, listOf())
                it.value.tree.isVisible = false
            }
        }
        
        // Handle display status and expand trees
        handleDisplayStatus()
        TreeUtil.expandAll(getTree(TreeType.ISSUES, isOld = false))
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

    private fun handleFileOpen(file: VirtualFile?): Boolean {
        if (file == null) {
            showNoFileMessage()
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

    private fun showNoFileMessage() {
        val statusText = issuesPanel.emptyText
        statusText.text = "No file opened in the editor"
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

        val isBound = Settings.getSettingsFor(project).isBound
        summaryPanel.setHotspotsEnabled(isBound)
        summaryPanel.setTaintsEnabled(isBound)
        summaryPanel.setDependencyRisksEnabled(isBound)

        runOnUiThread(project) { handleDisplayStatus() }
    }

    fun trySelectIssueForCodeFix(issue: LiveIssue) {
        selectAndOpenCodeFixTab(issue)
    }

    fun <T : Finding> trySelectFilteredIssue(issue: LiveIssue?, showFinding: ShowFinding<T>) {
        updateOnSelect(issue, showFinding)
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

}
