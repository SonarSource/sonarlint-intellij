package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesCache
import org.sonarlint.intellij.messages.StatusListener
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.util.SonarLintActions

private const val SPLIT_PROPORTION_PROPERTY = "SONARLINT_ISSUES_SPLIT_PROPORTION"

class CurrentFilePanel(project: Project) : CurrentFileFindingsPanel(project) {

    private val issuesPanel: JBPanelWithEmptyText
    private val treeScrollPane: JScrollPane
    private val analyzeCurrentFileAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
    private val restartSonarLintAction = SonarLintActions.getInstance().restartSonarLintAction()
    private val summaryPanel: CurrentFileSummaryPanel
    private val filtersPanel: FiltersPanel

    private var currentFile: VirtualFile? = null
    private var isMqrMode = false

    init {
        filtersPanel = FiltersPanel(
            { refreshView() },
            { sortMode ->
                treeBuilder.setSortMode(SortMode.valueOf(sortMode.name))
                oldTreeBuilder.setSortMode(SortMode.valueOf(sortMode.name))
                hotspotTreeBuilder.setSortMode(SortMode.valueOf(sortMode.name))
                oldHotspotTreeBuilder.setSortMode(SortMode.valueOf(sortMode.name))
                taintTreeBuilder.setSortMode(SortMode.valueOf(sortMode.name))
                oldTaintTreeBuilder.setSortMode(SortMode.valueOf(sortMode.name))
            }
        )

        summaryPanel = CurrentFileSummaryPanel({ selected ->
            tree.isVisible = !selected
            if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) {
                oldTree.isVisible = !selected
            }
            updateSummaryButtons()
        },{ selected ->
            hotspotTree.isVisible = !selected
            if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) {
                oldHotspotTree.isVisible = !selected
            }
            updateSummaryButtons()
        },{ selected ->
            taintTree.isVisible = !selected
            if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) {
                oldTaintTree.isVisible = !selected
            }
            updateSummaryButtons()
        }, { selected ->
            filtersPanel.isVisible = selected
        })

        setToolbar(actions())

        // --- Issues Section (existing) ---
        val treePanel = JBPanel<CurrentFilePanel>(VerticalFlowLayout(0, 0)).apply {
            add(tree)
            add(oldTree)
            add(hotspotTree)
            add(oldHotspotTree)
            add(taintTree)
            add(oldTaintTree)
        }
        tree.addTreeSelectionListener { _ ->
            if (!tree.isSelectionEmpty) {
                oldTree.clearSelection()
                hotspotTree.clearSelection()
                oldHotspotTree.clearSelection()
                taintTree.clearSelection()
                oldTaintTree.clearSelection()
                updateDetailsPanelForFinding(tree)
            }
        }
        oldTree.addTreeSelectionListener { _ ->
            if (!oldTree.isSelectionEmpty) {
                tree.clearSelection()
                hotspotTree.clearSelection()
                oldHotspotTree.clearSelection()
                taintTree.clearSelection()
                oldTaintTree.clearSelection()
                updateDetailsPanelForFinding(oldTree)
            }
        }
        hotspotTree.addTreeSelectionListener { _ ->
            if (!tree.isSelectionEmpty) {
                tree.clearSelection()
                oldTree.clearSelection()
                oldHotspotTree.clearSelection()
                taintTree.clearSelection()
                oldTaintTree.clearSelection()
                updateDetailsPanelForFinding(hotspotTree)
            }
        }
        oldHotspotTree.addTreeSelectionListener { _ ->
            if (!oldTree.isSelectionEmpty) {
                tree.clearSelection()
                oldTree.clearSelection()
                hotspotTree.clearSelection()
                taintTree.clearSelection()
                oldTaintTree.clearSelection()
                updateDetailsPanelForFinding(oldHotspotTree)
            }
        }
        taintTree.addTreeSelectionListener { _ ->
            if (!tree.isSelectionEmpty) {
                tree.clearSelection()
                oldTree.clearSelection()
                hotspotTree.clearSelection()
                oldHotspotTree.clearSelection()
                oldTaintTree.clearSelection()
                updateDetailsPanelForFinding(taintTree)
            }
        }
        oldTaintTree.addTreeSelectionListener { _ ->
            if (!oldTree.isSelectionEmpty) {
                tree.clearSelection()
                oldTree.clearSelection()
                hotspotTree.clearSelection()
                oldHotspotTree.clearSelection()
                taintTree.clearSelection()
                updateDetailsPanelForFinding(oldTaintTree)
            }
        }

        setupExpandCollapseMenus()

        treeScrollPane = ScrollPaneFactory.createScrollPane(treePanel, true)

        issuesPanel = JBPanelWithEmptyText(BorderLayout())
        issuesPanel.add(treeScrollPane, BorderLayout.CENTER)
        disableEmptyDisplay()

        // --- Compose main panel ---
        val headerPanel = JBPanel<CurrentFilePanel>(VerticalFlowLayout(0, 0)).apply {
            add(summaryPanel)
            add(filtersPanel)
        }

        // New: visually distinct card-like panel for header
        val headerCardPanel = JBPanel<CurrentFilePanel>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.empty(),  // outer
                JBUI.Borders.customLineBottom(JBColor.LIGHT_GRAY),  // subtle bottom border
                JBUI.Borders.empty(4) // inner padding
            )
            add(headerPanel, BorderLayout.CENTER)
        }

        val verticalContainer = JBPanel<CurrentFilePanel>(BorderLayout()).apply {
            add(headerCardPanel, BorderLayout.NORTH) // use headerCardPanel instead of headerPanel
            add(issuesPanel, BorderLayout.CENTER)
        }

        val mainPanel = JBPanel<CurrentFilePanel>(BorderLayout()).apply {
            add(verticalContainer, BorderLayout.CENTER)
            add(CurrentFileStatusPanel(project), BorderLayout.PAGE_END)
        }

        findingDetailsPanel.minimumSize = Dimension(350, 200)
        val splitter = SonarLintToolWindowFactory.createSplitter(
            project,
            this,
            this,
            mainPanel,
            findingDetailsPanel,
            SPLIT_PROPORTION_PROPERTY,
            0.5f
        )

        handleDisplayStatus()

        super.setContent(splitter)
        project.messageBus.connect().subscribe(
            StatusListener.SONARLINT_STATUS_TOPIC,
            StatusListener { _ -> runOnUiThread(project) { this.refreshToolbar() } }
        )
    }

    fun refreshView() {
        runOnUiThread(project) { this.update(currentFile) }
    }

    private fun actions(): List<AnAction> {
        return listOf(
            ActionManager.getInstance().getAction("SonarLint.SetFocusNewCode"),
            SonarLintActions.getInstance().analyzeCurrentFileAction(),
            ActionManager.getInstance().getAction("SonarLint.toolwindow.Cancel"),
            ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"),
            SonarLintActions.getInstance().clearIssues()
        )
    }

    fun update(file: VirtualFile?) {
        this.currentFile = file
        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        val statusText = issuesPanel.emptyText
        val backendIsAlive = getService(BackendService::class.java).isAlive()
        if (!backendIsAlive) {
            statusText.text = RestartBackendAction.Companion.SONARLINT_ERROR_MSG
            statusText.appendLine("Restart SonarQube for IDE Service", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
                ActionUtil.invokeAction(
                    restartSonarLintAction,
                    this,
                    TOOL_WINDOW_ID,
                    null,
                    null
                )
            }
            enableEmptyDisplay()
            populateSubTree(tree, treeBuilder, listOf())
            populateSubTree(oldTree, oldTreeBuilder, listOf())
            populateSubTree(hotspotTree, hotspotTreeBuilder, listOf())
            populateSubTree(oldHotspotTree, oldHotspotTreeBuilder, listOf())
            populateSubTree(taintTree, taintTreeBuilder, listOf())
            populateSubTree(oldTaintTree, oldTaintTreeBuilder, listOf())
            return
        }

        if (file == null) {
            statusText.text = "No file opened in the editor"
            enableEmptyDisplay()
            populateSubTree(tree, treeBuilder, listOf())
            populateSubTree(oldTree, oldTreeBuilder, listOf())
            populateSubTree(hotspotTree, hotspotTreeBuilder, listOf())
            populateSubTree(oldHotspotTree, oldHotspotTreeBuilder, listOf())
            populateSubTree(taintTree, taintTreeBuilder, listOf())
            populateSubTree(oldTaintTree, oldTaintTreeBuilder, listOf())
            return
        }

        // --- Filtering logic ---
        // Issues
        val issues = onTheFlyFindingsHolder.getIssuesForFile(file)
        // Determine if MQR mode is needed
        val newIsMqrMode = issues.any { it.getHighestImpact() != null }
        if (newIsMqrMode != isMqrMode) {
            isMqrMode = newIsMqrMode
            val newOptions = if (isMqrMode) MQR_IMPACTS else STANDARD_SEVERITIES
            val prev = filtersPanel.severityCombo.selectedItem as String?
            filtersPanel.severityCombo.setModel(DefaultComboBoxModel<String>(newOptions))
            // Try to keep the same selection if possible
            if (prev != null) {
                for (i in newOptions.indices) {
                    if (newOptions[i].equals(prev, ignoreCase = true)) {
                        filtersPanel.severityCombo.selectedIndex = i
                        break
                    }
                }
            }
        }
        val filteredIssues = issues.stream()
            .filter { i ->
                if (filtersPanel.filterSeverity == "All") return@filter true
                return@filter if (isMqrMode) {
                    i.getHighestImpact() != null && i.getHighestImpact()?.name.equals(filtersPanel.filterSeverity, ignoreCase = true)
                } else {
                    i.userSeverity != null && i.userSeverity?.name.equals(filtersPanel.filterSeverity, ignoreCase = true)
                }
            }
            .filter { i -> filtersPanel.filterStatus == "All" || (filtersPanel.filterStatus == "Open" != i.isResolved()) }
            .filter { i ->
                filtersPanel.filterText.isEmpty() || i.message != null && i.message.lowercase(Locale.getDefault()).contains(
                    filtersPanel.filterText.lowercase(
                        Locale.getDefault()
                    )
                ) || i.getRuleKey().lowercase(Locale.getDefault()).contains(filtersPanel.filterText.lowercase(Locale.getDefault())) || i.file()
                    .name.lowercase(Locale.getDefault()).contains(filtersPanel.filterText.lowercase(Locale.getDefault()))
            }
            .filter { i -> !filtersPanel.quickFixCheckBox.isSelected || i.quickFixes().isNotEmpty() || i.isAiCodeFixable() }
            .toList()
        // Security Hotspots
        val hotspots = onTheFlyFindingsHolder.getSecurityHotspotsForFile(file)
        val filteredHotspots = hotspots.stream()
            .filter { h ->
                filtersPanel.filterSeverity == "All" || h.vulnerabilityProbability.name.equals(
                    filtersPanel.filterSeverity,
                    ignoreCase = true
                )
            }
            .filter { h -> filtersPanel.filterStatus == "All" || (filtersPanel.filterStatus == "Open" != h.isResolved()) }
            .filter { h ->
                filtersPanel.filterText.isEmpty() || h.message != null && h.message.lowercase(Locale.getDefault()).contains(
                    filtersPanel.filterText.lowercase(
                        Locale.getDefault()
                    )
                ) || h.getRuleKey().lowercase(Locale.getDefault()).contains(filtersPanel.filterText.lowercase(Locale.getDefault())) || h.file()
                    .name.lowercase(
                        Locale.getDefault()
                    ).contains(filtersPanel.filterText.lowercase(Locale.getDefault()))
            }
            .filter { h -> !filtersPanel.quickFixCheckBox.isSelected || h.quickFixes().isNotEmpty() || h.isAiCodeFixable() }
            .toList()
        // Taint Vulnerabilities
        val taintCache = getService(project, TaintVulnerabilitiesCache::class.java)
        val taints = taintCache.getTaintVulnerabilitiesForFile(file)
        val filteredTaints = taints.stream()
            .filter { t -> filtersPanel.filterStatus == "All" || (filtersPanel.filterStatus == "Open" != t.isResolved()) }
            .filter { t ->
                filtersPanel.filterText.isEmpty() ||
                    (t.getRuleDescriptionContextKey() != null && t.getRuleDescriptionContextKey()!!
                        .lowercase(Locale.getDefault()).contains(filtersPanel.filterText.lowercase(Locale.getDefault()))) ||
                    (t.getRuleKey().lowercase(Locale.getDefault()).contains(filtersPanel.filterText.lowercase(Locale.getDefault()))) ||
                    (t.file() != null && t.file()?.name != null && t.file()
                        ?.name?.lowercase(Locale.getDefault())?.contains(filtersPanel.filterText.lowercase(Locale.getDefault())) == true)
            }
            .filter { i -> !filtersPanel.quickFixCheckBox.isSelected || i.isAiCodeFixable() }
            .toList()

        // --- Issues (existing logic, but with filteredIssues) ---
        if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) {
            val oldIssues = filteredIssues.filter { !it.isOnNewCode() }.toList()
            val newIssues = filteredIssues.filter { it.isOnNewCode() }.toList()
            populateSubTree(tree, treeBuilder, newIssues)
            populateSubTree(oldTree, oldTreeBuilder, oldIssues)
            oldTree.isVisible = !summaryPanel.areIssuesEnabled()
            updateIcon(file, newIssues)
            this.currentFile?.let { getService(project, EditorDecorator::class.java).createGutterIconForIssues(it, newIssues) }
        } else {
            populateSubTree(tree, treeBuilder, filteredIssues)
            populateSubTree(oldTree, oldTreeBuilder, listOf())
            oldTree.isVisible = false
            updateIcon(file, filteredIssues)
            this.currentFile?.let { getService(project, EditorDecorator::class.java).createGutterIconForIssues(it, filteredIssues) }
        }

        if (Settings.getSettingsFor(project).isBound) {
            // --- Security Hotspots ---
            if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) {
                val newHotspots = filteredHotspots.filter { it.isOnNewCode() }.toList()
                val oldHotspots = filteredHotspots.filter { !it.isOnNewCode() }.toList()
                populateSubTree(hotspotTree, hotspotTreeBuilder, newHotspots)
                populateSubTree(oldHotspotTree, oldHotspotTreeBuilder, oldHotspots)
                oldHotspotTree.isVisible = !summaryPanel.areHotspotsEnabled()
            } else {
                populateSubTree(hotspotTree, hotspotTreeBuilder, filteredHotspots)
                populateSubTree(oldHotspotTree, oldHotspotTreeBuilder, listOf())
                oldHotspotTree.isVisible = false
            }
            hotspotTree.isVisible = !summaryPanel.areHotspotsEnabled()
            hotspotTree.showsRootHandles = !filteredHotspots.isEmpty()
            oldHotspotTree.showsRootHandles = !filteredHotspots.isEmpty()

            // --- Taint Vulnerabilities ---
            if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) {
                val newTaints = filteredTaints.filter { it.isOnNewCode() }.toList()
                val oldTaints = filteredTaints.filter { !it.isOnNewCode() }.toList()
                populateSubTree(taintTree, taintTreeBuilder, newTaints)
                populateSubTree(oldTaintTree, oldTaintTreeBuilder, oldTaints)
                oldTaintTree.isVisible = !summaryPanel.areTaintsEnabled()
            } else {
                populateSubTree(taintTree, taintTreeBuilder, filteredTaints)
                populateSubTree(oldTaintTree, oldTaintTreeBuilder, listOf())
                oldTaintTree.isVisible = false
            }
            taintTree.isVisible = !summaryPanel.areTaintsEnabled()
            taintTree.showsRootHandles = !filteredTaints.isEmpty()
            oldTaintTree.showsRootHandles = !filteredTaints.isEmpty()
        } else {
            populateSubTree(hotspotTree, hotspotTreeBuilder, listOf())
            hotspotTree.isVisible = false
            populateSubTree(oldHotspotTree, oldHotspotTreeBuilder, listOf())
            oldHotspotTree.isVisible = false
            populateSubTree(taintTree, taintTreeBuilder, listOf())
            taintTree.isVisible = false
            populateSubTree(oldTaintTree, oldTaintTreeBuilder, listOf())
            oldTaintTree.isVisible = false
        }

        // --- Empty state messages ---
        if (filteredIssues.isEmpty() && filteredHotspots.isEmpty() && filteredTaints.isEmpty()) {
            statusText.text = "No findings match your filters"
            enableEmptyDisplay()
        } else {
            disableEmptyDisplay()
        }

        handleDisplayStatus()
        TreeUtil.expandAll(tree)
        updateSummaryButtons()
    }

    fun isDisplayingFindings(): Boolean {
        return (!summaryPanel.areIssuesEnabled() && (treeBuilder.numberOfDisplayedFindings() >= 0 || oldTreeBuilder.numberOfDisplayedFindings() >= 0))
            || (!summaryPanel.areHotspotsEnabled() && (hotspotTreeBuilder.numberOfDisplayedFindings() >= 0 || oldHotspotTreeBuilder.numberOfDisplayedFindings() >= 0))
            || (!summaryPanel.areTaintsEnabled() && (taintTreeBuilder.numberOfDisplayedFindings() >= 0 || oldTaintTreeBuilder.numberOfDisplayedFindings() >= 0))
    }

    fun hasAnyFindings(): Boolean {
        return !treeBuilder.isEmpty() || !oldTreeBuilder.isEmpty() || !hotspotTreeBuilder.isEmpty() || !oldHotspotTreeBuilder.isEmpty() || !taintTreeBuilder.isEmpty() || !oldTaintTreeBuilder.isEmpty()
    }

    private fun updateSummaryButtons() {
        val issueSummaryUiModel = treeBuilder.getSummaryUiModel()
        val hotspotSummaryUiModel = hotspotTreeBuilder.getSummaryUiModel()
        val taintSummaryUiModel = taintTreeBuilder.getSummaryUiModel()

        summaryPanel.updateIssues(treeBuilder.numberOfDisplayedFindings(), issueSummaryUiModel)
        summaryPanel.updateHotspots(hotspotTreeBuilder.numberOfDisplayedFindings(), hotspotSummaryUiModel)
        summaryPanel.updateTaints(taintTreeBuilder.numberOfDisplayedFindings(), taintSummaryUiModel)

        val isBound = Settings.getSettingsFor(project).isBound
        summaryPanel.setHotspotsEnabled(isBound)
        summaryPanel.setTaintsEnabled(isBound)

        // Dynamically add/remove status filter based on binding
        updateStatusFilterVisibility()

        runOnUiThread(project) { this.handleDisplayStatus() }
    }

    private fun updateStatusFilterVisibility() {
        val isBound = Settings.getSettingsFor(project).isBound
        var statusLabelPresent = false
        var statusComboPresent = false
        for (i in 0 until filtersPanel.componentCount) {
            if (filtersPanel.getComponent(i) === filtersPanel.statusLabel) statusLabelPresent = true
            if (filtersPanel.getComponent(i) === filtersPanel.statusCombo) statusComboPresent = true
        }
        if (isBound && !statusLabelPresent && !statusComboPresent) {
            // Insert after severityCombo (find its index)
            var idx = -1
            for (i in 0 until filtersPanel.componentCount) {
                if (filtersPanel.getComponent(i) === filtersPanel.severityCombo) {
                    idx = i
                    break
                }
            }
            if (idx != -1) {
                filtersPanel.add(Box.createRigidArea(Dimension(4, 0)), idx + 3)
                filtersPanel.add(filtersPanel.statusLabel, idx + 2)
                filtersPanel.add(Box.createRigidArea(Dimension(4, 0)), idx + 5)
                filtersPanel.add(filtersPanel.statusCombo, idx + 4)
            } else {
                filtersPanel.add(Box.createRigidArea(Dimension(4, 0)))
                filtersPanel.add(filtersPanel.statusLabel)
                filtersPanel.add(Box.createRigidArea(Dimension(4, 0)))
                filtersPanel.add(filtersPanel.statusCombo)
            }
            filtersPanel.revalidate()
            filtersPanel.repaint()
        } else if (!isBound && (statusLabelPresent || statusComboPresent)) {
            filtersPanel.remove(filtersPanel.statusLabel)
            filtersPanel.remove(filtersPanel.statusCombo)
            // Remove any adjacent rigid areas (if present)
            for (i in filtersPanel.componentCount - 1 downTo 0) {
                val c = filtersPanel.getComponent(i)
                if (c is Box.Filler) {
                    filtersPanel.remove(i)
                }
            }
            filtersPanel.revalidate()
            filtersPanel.repaint()
        }
    }

    private fun <T : Finding> populateSubTree(tree: Tree, treeBuilder: SingleFileTreeModelBuilder<T>, issues: List<T>) {
        treeBuilder.updateModel(currentFile, issues)
        tree.showsRootHandles = issues.isNotEmpty()
    }

    private fun updateDetailsPanelForFinding(tree: Tree) {
        val selected = tree.lastSelectedPathComponent
        when (selected) {
            is IssueNode -> findingDetailsPanel.show(selected.issue(), false)
            is LiveSecurityHotspotNode -> findingDetailsPanel.show(selected.hotspot, false)
            is LocalTaintVulnerability -> findingDetailsPanel.show(selected, false)
            else -> findingDetailsPanel.clear()
        }
    }

    private fun setupExpandCollapseMenus() {
        addExpandCollapseActions(tree)
        addExpandCollapseActions(oldTree)
        addExpandCollapseActions(hotspotTree)
        addExpandCollapseActions(oldHotspotTree)
        addExpandCollapseActions(taintTree)
        addExpandCollapseActions(oldTaintTree)
    }

    private fun addExpandCollapseActions(tree: Tree) {
        tree.componentPopupMenu
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    val menu = JPopupMenu()
                    val expandItem = JMenuItem("Expand All")
                    expandItem.addActionListener { _ -> TreeUtil.expandAll(tree) }
                    menu.add(expandItem)
                    val collapseItem = JMenuItem("Collapse All")
                    collapseItem.addActionListener { _ -> TreeUtil.collapseAll(tree, 0) }
                    menu.add(collapseItem)
                    menu.show(tree, e.x, e.y)
                }
            }
        })
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

    fun allowResolvedFindings(allowResolved: Boolean) {
        treeBuilder.allowResolvedFindings(allowResolved)
        oldTreeBuilder.allowResolvedFindings(allowResolved)
        hotspotTreeBuilder.allowResolvedFindings(allowResolved)
        oldHotspotTreeBuilder.allowResolvedFindings(allowResolved)
        taintTreeBuilder.allowResolvedFindings(allowResolved)
        oldTaintTreeBuilder.allowResolvedFindings(allowResolved)
        refreshModel()
        runOnUiThread(project) { this.handleDisplayStatus() }
    }

    fun refreshModel() {
        treeBuilder.refreshModel()
        oldTreeBuilder.refreshModel()
        hotspotTreeBuilder.refreshModel()
        oldHotspotTreeBuilder.refreshModel()
        taintTreeBuilder.refreshModel()
        oldTaintTreeBuilder.refreshModel()
        runOnUiThread(project) { TreeUtil.expandAll(tree) }
    }

    fun remove(issue: LiveIssue) {
        treeBuilder.removeFinding(issue)
        oldTreeBuilder.removeFinding(issue)
    }

    fun getIssueFiltered(issueKey: String): LiveIssue? {
        var issue = treeBuilder.findFindingByKey(issueKey)
        if (issue == null) {
            issue = oldTreeBuilder.findFindingByKey(issueKey)
        }
        return issue
    }

    fun doesIssueExist(issueKey: String): Boolean {
        return treeBuilder.findFindingByKey(issueKey) != null || oldTreeBuilder.findFindingByKey(issueKey) != null
    }

    fun trySelectIssueForCodeFix(issue: LiveIssue) {
        selectAndOpenCodeFixTab(issue)
    }

    fun <T : Finding> trySelectFilteredIssue(issue: LiveIssue?, showFinding: ShowFinding<T>) {
        updateOnSelect(issue, showFinding)
    }

    private fun handleDisplayStatus() {
        val emptyText = issuesPanel.emptyText
        if (getService(project, AnalysisReadinessCache::class.java).isProjectReady) {
            setAnalysisIsReady()
        } else {
            emptyText.text = "Waiting for SonarQube for IDE to be ready"
            enableEmptyDisplay()
        }

        val hasFindings = hasAnyFindings()
        val hasDisplayedFindings = isDisplayingFindings()
        if (!hasFindings) {
            emptyText.text = "No issues to display"
            enableEmptyDisplay()
        } else if (!hasDisplayedFindings) {
            emptyText.text = "No issues to display due to the current filtering"
            enableEmptyDisplay()
        } else {
            disableEmptyDisplay()
        }
    }

    private fun disableEmptyDisplay() {
        treeScrollPane.isVisible = true
    }

    private fun enableEmptyDisplay() {
        treeScrollPane.isVisible = false
    }

    fun updateIcon(file: VirtualFile?, issues: List<LiveIssue>) {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.let {
            val isEmpty = issues.filter { i -> !i.isResolved() }.toSet().isEmpty();
            doUpdateIcon(file, isEmpty, it)
        }
    }

    private fun doUpdateIcon(file: VirtualFile?, isEmpty: Boolean, toolWindow: ToolWindow) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val empty = file == null || isEmpty
        toolWindow.setIcon(if (empty) SonarLintIcons.SONARQUBE_FOR_INTELLIJ_EMPTY_TOOLWINDOW else SonarLintIcons.SONARQUBE_FOR_INTELLIJ_TOOLWINDOW)
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

}
