/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.ui.vulnerabilities

import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.General.Information
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.HorizontalLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeSelectionModel
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.actions.OpenInBrowserAction
import org.sonarlint.intellij.actions.RefreshTaintVulnerabilitiesAction
import org.sonarlint.intellij.actions.SonarConfigureProject
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.TAINT_VULNERABILITIES_LINK
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.TextRangeMatcher
import org.sonarlint.intellij.finding.issue.vulnerabilities.FoundTaintVulnerabilities
import org.sonarlint.intellij.finding.issue.vulnerabilities.InvalidBinding
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.issue.vulnerabilities.NoBinding
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesStatus
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.CardPanel
import org.sonarlint.intellij.ui.CurrentFilePanel
import org.sonarlint.intellij.ui.SonarLintRulePanel
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTree
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTreeUpdater
import org.sonarlint.intellij.ui.vulnerabilities.tree.filter.FocusFilter
import org.sonarlint.intellij.ui.vulnerabilities.tree.filter.ResolutionFilter
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY
import org.sonarlint.intellij.util.SonarLintActions
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.runOnPooledThread

private const val SPLIT_PROPORTION_PROPERTY = "SONARLINT_TAINT_VULNERABILITIES_SPLIT_PROPORTION"
private const val DEFAULT_SPLIT_PROPORTION = 0.5f

private const val NO_BINDING_CARD_ID = "NO_BINDING_CARD"
private const val NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID = "NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID"
private const val INVALID_BINDING_CARD_ID = "INVALID_BINDING_CARD"
private const val NO_ISSUES_CARD_ID = "NO_ISSUES_CARD"
private const val TREE_CARD_ID = "TREE_CARD"

private const val LEARN_MORE = "Learn More"
private const val TOOLBAR_GROUP_ID = "TaintVulnerabilities"

class TaintVulnerabilitiesPanel(private val project: Project) : SimpleToolWindowPanel(false, true), DataProvider, Disposable {

    private lateinit var tree: TaintVulnerabilityTree
    private lateinit var oldTree: TaintVulnerabilityTree
    private val treeSummary = TreeSummary(project, TreeContentKind.ISSUES, false)
    private val oldTreeSummary = TreeSummary(project, TreeContentKind.ISSUES, true)
    private lateinit var treeListeners: Map<TaintVulnerabilityTree, List<TreeSelectionListener>>
    private val treePanel: JBPanel<TaintVulnerabilitiesPanel>
    private val rulePanel = SonarLintRulePanel(project, this)
    private val cards = CardPanel()
    private val noVulnerabilitiesPanel: JBPanelWithEmptyText
    private var currentStatus: FoundTaintVulnerabilities? = null

    private val taintVulnerabilityTreeUpdater = TaintVulnerabilityTreeUpdater(treeSummary)
    private val oldTaintVulnerabilityTreeUpdater = TaintVulnerabilityTreeUpdater(oldTreeSummary)

    init {
        val globalSettings = getGlobalSettings()
        cards.add(centeredLabel("The project is not bound to SonarCloud/SonarQube", "Configure Binding", SonarConfigureProject()), NO_BINDING_CARD_ID)
        cards.add(centeredLabel("The project binding is invalid", "Edit Binding", SonarConfigureProject()), INVALID_BINDING_CARD_ID)
        cards.add(centeredLabel("No taint vulnerabilities shown due to the current filtering", "Show Resolved Taint Vulnerabilities",
            SonarLintActions.getInstance().includeResolvedTaintVulnerabilitiesAction()), NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID)
        noVulnerabilitiesPanel = centeredLabel("", "", null)
        cards.add(noVulnerabilitiesPanel, NO_ISSUES_CARD_ID)
        rulePanel.minimumSize = Dimension(350, 200)

        initTrees()
        treePanel = JBPanel<TaintVulnerabilitiesPanel>(VerticalFlowLayout(0, 0))
        treePanel.add(tree)
        treePanel.add(oldTree)
        cards.add(createSplitter(project, this, this, treePanel, rulePanel, SPLIT_PROPORTION_PROPERTY, DEFAULT_SPLIT_PROPORTION),
            TREE_CARD_ID
        )

        val issuesPanel = JPanel(BorderLayout())
        if (!globalSettings.isTaintVulnerabilitiesTabDisclaimerDismissed) {
            issuesPanel.add(createDisclaimer(), BorderLayout.NORTH)
        }
        issuesPanel.add(cards.container, BorderLayout.CENTER)
        setContent(issuesPanel)

        val sonarLintActions = SonarLintActions.getInstance()
        setupToolbar(listOf(
            ActionManager.getInstance().getAction("SonarLint.SetFocusNewCode"),
            RefreshTaintVulnerabilitiesAction(),
            sonarLintActions.includeResolvedTaintVulnerabilitiesAction(),
            sonarLintActions.configure(),
            OpenInBrowserAction(LEARN_MORE, "Learn more about taint vulnerabilities in SonarLint", TAINT_VULNERABILITIES_LINK, AllIcons.Actions.Help)
        ))
        applyFocusOnNewCodeSettings()
    }

    private fun centeredLabel(textLabel: String, actionText: String?, action: AnAction?): JBPanelWithEmptyText {
        val labelPanel = JBPanelWithEmptyText(HorizontalLayout(5))
        val text = labelPanel.emptyText
        text.setText(textLabel)
        if (action != null && actionText != null) {
            text.appendLine(
                actionText, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
            ) { _: ActionEvent? ->
                ActionUtil.invokeAction(
                    action,
                    labelPanel,
                    CurrentFilePanel.SONARLINT_TOOLWINDOW_ID,
                    null,
                    null
                )
            }
        }
        return labelPanel
    }

    private fun taintTreeSelectionChanged(tree: TaintVulnerabilityTree, secondTree: TaintVulnerabilityTree) {
        if (!tree.isSelectionEmpty) {
            secondTree.clearSelection()
        }
        val selectedTaintNode = tree.getSelectedNodes(LocalTaintVulnerability::class.java, null)
        if (selectedTaintNode.isNotEmpty()) {
            setSelectedVulnerability(selectedTaintNode[0])
        } else {
            rulePanel.clear()
            val highlighting = getService(project, EditorDecorator::class.java)
            highlighting.removeHighlights()
        }
    }

    private fun createDisclaimer(): StripePanel {
        val stripePanel = StripePanel("This tab displays taint vulnerabilities detected by SonarCloud or SonarQube. SonarLint does not detect those issues locally.", Information)
        stripePanel.addAction(LEARN_MORE, OpenInBrowserAction(LEARN_MORE, "Learn more about taint vulnerabilities in SonarLint", TAINT_VULNERABILITIES_LINK))
        stripePanel.addAction("Dismiss", object : AbstractSonarAction() {
            override fun actionPerformed(e: AnActionEvent) {
                stripePanel.isVisible = false
                getGlobalSettings().dismissTaintVulnerabilitiesTabDisclaimer()
            }
        })
        return stripePanel
    }

    private fun setupToolbar(actions: List<AnAction>) {
        val group = DefaultActionGroup()
        actions.forEach { group.add(it) }
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_GROUP_ID, group, false)
        toolbar.targetComponent = this
        val toolBarBox = Box.createHorizontalBox()
        toolBarBox.add(toolbar.component)
        setToolbar(toolBarBox)
        toolbar.component.isVisible = true
    }

    private fun showCard(id: String) {
        cards.show(id)
    }

    fun allowResolvedTaintVulnerabilities(includeResolved: Boolean) {
        taintVulnerabilityTreeUpdater.resolutionFilter = if (includeResolved) ResolutionFilter.ALL else ResolutionFilter.OPEN_ONLY
        oldTaintVulnerabilityTreeUpdater.resolutionFilter = if (includeResolved) ResolutionFilter.ALL else ResolutionFilter.OPEN_ONLY
        switchCard()
    }

    fun populate(status: TaintVulnerabilitiesStatus) {
        val highlighting = getService(project, EditorDecorator::class.java)
        when (status) {
            is NoBinding -> {
                showCard(NO_BINDING_CARD_ID)
                highlighting.removeHighlights()
            }

            is InvalidBinding -> {
                showCard(INVALID_BINDING_CARD_ID)
                highlighting.removeHighlights()
            }

            is FoundTaintVulnerabilities -> {
                if (status.isEmpty()) {
                    showNoVulnerabilitiesLabel()
                    highlighting.removeHighlights()
                } else {
                    populateTrees(status.vulnerabilities())
                }
            }
        }
    }

    private fun populateTrees(taintVulnerabilities: List<LocalTaintVulnerability>) {
        taintVulnerabilityTreeUpdater.taintVulnerabilities = taintVulnerabilities
        oldTaintVulnerabilityTreeUpdater.taintVulnerabilities = taintVulnerabilities
        switchCard()
    }

    fun switchCard() {
        if (taintVulnerabilityTreeUpdater.taintVulnerabilities.isEmpty() && oldTaintVulnerabilityTreeUpdater.taintVulnerabilities.isEmpty()) {
            val highlighting = getService(project, EditorDecorator::class.java)
            showNoVulnerabilitiesLabel()
            highlighting.removeHighlights()
        } else {
            if (taintVulnerabilityTreeUpdater.filteredTaintVulnerabilities.isEmpty() && oldTaintVulnerabilityTreeUpdater.filteredTaintVulnerabilities.isEmpty()) {
                showCard(NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID)
            } else {
                showCard(TREE_CARD_ID)

            }
        }
    }

    fun primaryCount(): Int {
        val firstTreeFilteredCount = taintVulnerabilityTreeUpdater.filteredTaintVulnerabilities.size
        return if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) firstTreeFilteredCount else firstTreeFilteredCount + oldTaintVulnerabilityTreeUpdater.filteredTaintVulnerabilities.size
    }

//    private fun populateSubTree(tree: TaintVulnerabilityTree, model: TaintVulnerabilityTreeModelBuilder, taintIssues: Map<VirtualFile, List<LocalTaintVulnerability>>) {
//        val expandedPaths = TreeUtil.collectExpandedPaths(tree)
//        val selectionPath: TreePath? = tree.selectionPath
//        // Temporarily remove the listener to avoid transient selection events while changing the model
//        treeListeners[tree]?.forEach { listener -> tree.removeTreeSelectionListener(listener) }
//        try {
//            model.updateModel(taintIssues)
//            tree.showsRootHandles = taintIssues.isNotEmpty()
//            TreeUtil.restoreExpandedPaths(tree, expandedPaths)
//            if (selectionPath != null) {
//                TreeUtil.selectPath(tree, selectionPath)
//            } else {
//                expandDefault()
//            }
//        } finally {
//            treeListeners[tree]?.forEach { listener -> tree.addTreeSelectionListener(listener) }
//            updateRulePanelContent(tree)
//        }
//    }
//
//    private fun expandDefault() {
//        if (treeBuilder.numberIssues() < 30) {
//            TreeUtil.expand(tree, 2)
//        } else {
//            tree.expandRow(0)
//        }
//        oldTree.collapseRow(0)
//    }

    private fun showNoVulnerabilitiesLabel() {
        val serverConnection = getService(project, ProjectBindingManager::class.java).serverConnection
        noVulnerabilitiesPanel.withEmptyText("No vulnerabilities found for currently opened files in the latest analysis on ${serverConnection.productName}")
        showCard(NO_ISSUES_CARD_ID)
    }

    fun setSelectedVulnerability(vulnerability: LocalTaintVulnerability) {
        if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project) && !vulnerability.isOnNewCode()) {
            oldTree.setSelectedVulnerability(vulnerability)
        } else {
            tree.setSelectedVulnerability(vulnerability)
        }
    }

    fun remove(taintVulnerability: LocalTaintVulnerability) {
        taintVulnerabilityTreeUpdater.remove(taintVulnerability)
        oldTaintVulnerabilityTreeUpdater.remove(taintVulnerability)
        switchCard()
    }

    private fun updateRulePanelContent(tree: TaintVulnerabilityTree) {
        val highlighting = getService(project, EditorDecorator::class.java)
        val issue = tree.getIssueFromSelectedNode()
        if (issue == null) {
            rulePanel.clear()
            highlighting.removeHighlights()
        } else {
            val file = issue.file()
            if (file == null) {
                // FIXME can't we find a way to get the rule description?
                rulePanel.clear()
                highlighting.removeHighlights()
            } else {
                issue.module?.let { module -> rulePanel.setSelectedFinding(module, issue, issue.getRuleKey(), issue.getRuleDescriptionContextKey()) }
                        ?: rulePanel.clear()
                highlighting.highlight(issue)
            }
        }
    }

    fun applyFocusOnNewCodeSettings() {
        val shouldFocusOnNewCode = getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)
        taintVulnerabilityTreeUpdater.focusFilter = if (shouldFocusOnNewCode) FocusFilter.NEW_CODE else FocusFilter.ALL_CODE
        oldTaintVulnerabilityTreeUpdater.focusFilter = if (shouldFocusOnNewCode) FocusFilter.OLD_CODE else FocusFilter.ALL_CODE
        oldTree.isVisible = shouldFocusOnNewCode
    }

    private fun findFilteredTaintVulnerabilityByKey(key: String): LocalTaintVulnerability? {
        return taintVulnerabilityTreeUpdater.filteredTaintVulnerabilities.find { it.key() == key }
            ?: oldTaintVulnerabilityTreeUpdater.filteredTaintVulnerabilities.find { it.key() == key }
    }

    fun <T : Finding> trySelectFilteredTaintVulnerability(showFinding: ShowFinding<T>) {
        findFilteredTaintVulnerabilityByKey(showFinding.findingKey)?.let {
            setSelectedVulnerability(it)
            return
        }
        runOnPooledThread(project) {
            if (showFinding.codeSnippet == null) {
                SonarLintProjectNotifications.get(project)
                        .notifyUnableToOpenFinding("taint vulnerability",
                                "The taint vulnerability could not be detected by SonarLint in the current code.")
                return@runOnPooledThread
            }
            val matcher = TextRangeMatcher(project)
            val rangeMarker = computeReadActionSafely<RangeMarker>(project) {
                matcher.matchWithCode(showFinding.file, showFinding.textRange, showFinding.codeSnippet)
            }
            if (rangeMarker == null) {
                SonarLintProjectNotifications.get(project)
                        .notifyUnableToOpenFinding("taint vulnerability", "The taint vulnerability could not be detected by SonarLint in the current code.")
                return@runOnPooledThread
            }

            findModuleForFile(showFinding.file, project)?.let {
                runOnUiThread(project) {
                    rulePanel.setSelectedFinding(it, null, showFinding.ruleKey, null)
                    getService(project, EditorDecorator::class.java).highlightRange(rangeMarker)
                }
            }
        }
    }

    private fun initTrees() {
        tree = TaintVulnerabilityTree(project, taintVulnerabilityTreeUpdater)
        oldTree = TaintVulnerabilityTree(project, oldTaintVulnerabilityTreeUpdater)
        treeListeners = mapOf(
            tree to listOf(TreeSelectionListener { updateRulePanelContent(tree) }, TreeSelectionListener { taintTreeSelectionChanged(tree, oldTree) }),
            oldTree to listOf(TreeSelectionListener { updateRulePanelContent(oldTree) }, TreeSelectionListener { taintTreeSelectionChanged(oldTree, tree) })
        )

        listOf(tree, oldTree).forEach {
            treeListeners[it]?.forEach { listener -> it.addTreeSelectionListener(listener) }
            it.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (KeyEvent.VK_ESCAPE == e.keyCode) {
                        val highlighting = getService(
                            project,
                            EditorDecorator::class.java
                        )
                        highlighting.removeHighlights()
                    }
                }
            })
            it.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        }
    }

    override fun getData(dataId: String): Any? {
        return if (TAINT_VULNERABILITY_DATA_KEY.`is`(dataId)) {
            tree.getSelectedIssue() ?: oldTree.getSelectedIssue()
        } else null
    }

    override fun dispose() {
        // Nothing to do
    }

}
