/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.icons.AllIcons.General.Information
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.OccurenceNavigator.OccurenceInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.tools.SimpleActionGroup
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.tree.TreeUtil
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.actions.OpenTaintVulnerabilityDocumentationAction
import org.sonarlint.intellij.actions.RefreshTaintVulnerabilitiesAction
import org.sonarlint.intellij.actions.SonarConfigureProject
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.core.ProjectBindingManager
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
import org.sonarlint.intellij.ui.nodes.AbstractNode
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LocalTaintVulnerabilityNode
import org.sonarlint.intellij.ui.tree.TaintVulnerabilityTree
import org.sonarlint.intellij.ui.tree.TaintVulnerabilityTreeModelBuilder
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY
import org.sonarlint.intellij.util.SonarLintActions
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val SPLIT_PROPORTION_PROPERTY = "SONARLINT_TAINT_VULNERABILITIES_SPLIT_PROPORTION"
private const val DEFAULT_SPLIT_PROPORTION = 0.5f

private const val NO_BINDING_CARD_ID = "NO_BINDING_CARD"
private const val NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID = "NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID"
private const val INVALID_BINDING_CARD_ID = "INVALID_BINDING_CARD"
private const val NO_ISSUES_CARD_ID = "NO_ISSUES_CARD"
private const val TREE_CARD_ID = "TREE_CARD"

private const val TOOLBAR_GROUP_ID = "TaintVulnerabilities"

class TaintVulnerabilitiesPanel(private val project: Project) : SimpleToolWindowPanel(false, true),
    OccurenceNavigator, DataProvider, Disposable {

    private lateinit var tree: TaintVulnerabilityTree
    private lateinit var treeBuilder: TaintVulnerabilityTreeModelBuilder
    private lateinit var oldTree: TaintVulnerabilityTree
    private lateinit var oldTreeBuilder: TaintVulnerabilityTreeModelBuilder
    private val treeSummary = TreeSummary(project, TreeContentKind.ISSUES, false)
    private val oldTreeSummary = TreeSummary(project, TreeContentKind.ISSUES, true)
    private lateinit var treeListeners: Map<TaintVulnerabilityTree, List<TreeSelectionListener>>
    private val treePanel: JBPanel<TaintVulnerabilitiesPanel>
    private val rulePanel = SonarLintRulePanel(project, this)
    private val cards = CardPanel()
    private val noVulnerabilitiesPanel: JBPanelWithEmptyText
    private var currentStatus: FoundTaintVulnerabilities? = null

    init {
        val globalSettings = getGlobalSettings()
        cards.add(centeredLabel("The project is not bound to SonarQube/SonarCloud", "Configure Binding", SonarConfigureProject()), NO_BINDING_CARD_ID)
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
            OpenTaintVulnerabilityDocumentationAction()
        ))
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
            val selectedTaintNode = tree.getSelectedNodes(LocalTaintVulnerabilityNode::class.java, null)
            if (selectedTaintNode.isNotEmpty()) {
                setSelectedVulnerability(selectedTaintNode[0].issue)
            } else {
                rulePanel.clear()
                val highlighting = getService(project, EditorDecorator::class.java)
                highlighting.removeHighlights()
            }
    }

    private fun createDisclaimer(): StripePanel {
        val stripePanel = StripePanel("This tab displays taint vulnerabilities detected by SonarQube or SonarCloud. SonarLint does not detect those issues locally.", Information)
        stripePanel.addAction("Learn More", OpenTaintVulnerabilityDocumentationAction())
        stripePanel.addAction("Dismiss", object : AbstractSonarAction() {
            override fun actionPerformed(e: AnActionEvent) {
                stripePanel.isVisible = false
                getGlobalSettings().dismissTaintVulnerabilitiesTabDisclaimer()
            }
        })
        return stripePanel
    }

    private fun setupToolbar(actions: List<AnAction>) {
        val group = SimpleActionGroup()
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

    fun allowResolvedTaintVulnerabilities(allowResolved: Boolean) {
        treeBuilder.allowResolvedIssues(allowResolved)
        oldTreeBuilder.allowResolvedIssues(allowResolved)
        refreshModel()
    }

    fun refreshModel() {
        treeBuilder.refreshModel(project)
        oldTreeBuilder.refreshModel(project)
        if (treeBuilder.isEmptyWithFilteredIssues() || oldTreeBuilder.isEmptyWithFilteredIssues()) {
            showCard(NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID)
        } else {
            showCard(TREE_CARD_ID)
        }
        expandDefault()
    }

    fun populate(status: TaintVulnerabilitiesStatus) {
        val highlighting = getService(project, EditorDecorator::class.java)
        when (status) {
            is NoBinding  ->  {
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
                    populateTrees(status)
                    if (treeBuilder.isEmptyWithFilteredIssues() || oldTreeBuilder.isEmptyWithFilteredIssues()) {
                        showCard(NO_FILTERED_TAINT_VULNERABILITIES_CARD_ID)
                    } else {
                        showCard(TREE_CARD_ID)
                    }
                }
            }
        }
    }

    private fun populateTrees(status: FoundTaintVulnerabilities) {
        currentStatus = status
        if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)) {
            populateSubTree(tree, treeBuilder, status.newVulnerabilities())
            populateSubTree(oldTree, oldTreeBuilder, status.oldVulnerabilities())
            oldTree.isVisible = true
        } else {
            populateSubTree(tree, treeBuilder, status.byFile.mapValues { (_, issues) -> issues.toList() })
            populateSubTree(oldTree, oldTreeBuilder, mapOf())
            oldTree.isVisible = false
        }
    }

    private fun populateSubTree(tree: TaintVulnerabilityTree, model: TaintVulnerabilityTreeModelBuilder, taintIssues: Map<VirtualFile, List<LocalTaintVulnerability>>) {
        val expandedPaths = TreeUtil.collectExpandedPaths(tree)
        val selectionPath: TreePath? = tree.selectionPath
        // Temporarily remove the listener to avoid transient selection events while changing the model
        treeListeners[tree]?.forEach { listener -> tree.removeTreeSelectionListener(listener) }
        try {
            model.updateModel(taintIssues)
            tree.showsRootHandles = taintIssues.isNotEmpty()
            TreeUtil.restoreExpandedPaths(tree, expandedPaths)
            if (selectionPath != null) {
                TreeUtil.selectPath(tree, selectionPath)
            } else {
                expandDefault()
            }
        } finally {
            treeListeners[tree]?.forEach { listener -> tree.addTreeSelectionListener(listener) }
            updateRulePanelContent(tree)
        }
    }

    private fun expandDefault() {
        if (treeBuilder.numberIssues() < 30) {
            TreeUtil.expand(tree, 2)
        } else {
            tree.expandRow(0)
        }
        oldTree.collapseRow(0)
    }

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
        treeBuilder.remove(taintVulnerability)
        oldTreeBuilder.remove(taintVulnerability)
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
                val moduleForFile = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file)
                if (moduleForFile != null) {
                    rulePanel.setSelectedFinding(moduleForFile, issue, issue.getRuleKey(), issue.getRuleDescriptionContextKey())
                } else {
                    rulePanel.clear()
                }
                highlighting.highlight(issue)
            }
        }
    }

    fun refreshView() {
        currentStatus?.let { populateTrees(it) }
    }

    fun findTaintVulnerabilityByKey(taintKey: String): LocalTaintVulnerability? {
        return treeBuilder.findTaintByKey(taintKey) ?: oldTreeBuilder.findTaintByKey(taintKey)
    }

    fun <T: Finding> trySelectFilteredTaintVulnerability(taintVulnerability: LocalTaintVulnerability?, showFinding: ShowFinding<T>) {
        taintVulnerability?.let {
            setSelectedVulnerability(it)
            return
        }
        if (showFinding.codeSnippet == null) {
            SonarLintProjectNotifications.get(project)
                .notifyUnableToOpenFinding("taint vulnerability",
                    "The taint vulnerability could not be detected by SonarLint in the current code.")
            return
        }
        val matcher = TextRangeMatcher(project)
        val rangeMarker = computeReadActionSafely<RangeMarker>(project) {
            matcher.matchWithCode(showFinding.file, showFinding.textRange, showFinding.codeSnippet)
        }
        if (rangeMarker == null) {
            SonarLintProjectNotifications.get(project)
                .notifyUnableToOpenFinding("taint vulnerability", "The taint vulnerability could not be detected by SonarLint in the current code.")
            return
        }
        ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(showFinding.file)?.let {
            rulePanel.setSelectedFinding(it, null, showFinding.ruleKey, null)
            getService(project, EditorDecorator::class.java).highlightRange(rangeMarker)
        }
    }

    private fun initTrees() {
        treeBuilder = TaintVulnerabilityTreeModelBuilder(treeSummary)
        tree = TaintVulnerabilityTree(project, treeBuilder.model)
        oldTreeBuilder = TaintVulnerabilityTreeModelBuilder(oldTreeSummary)
        oldTree = TaintVulnerabilityTree(project, oldTreeBuilder.model)

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

    private fun occurrence(taintTree: TaintVulnerabilityTree, node: IssueNode?): OccurenceInfo? {
        if (node == null) {
            return null
        }
        val path = TreePath(node.path)
        taintTree.selectionModel.selectionPath = path
        taintTree.scrollPathToVisible(path)
        val range = node.issue().range
        val startOffset = range?.startOffset ?: 0
        return OccurenceInfo(
            OpenFileDescriptor(project, node.issue().psiFile().virtualFile, startOffset),
            -1,
            -1
        )
    }

    override fun hasNextOccurence(): Boolean {
        // relies on the assumption that a TreeNodes will always be the last row in the table view of the tree
        return tree.selectionPath?.let {
            val node = it.lastPathComponent as DefaultMutableTreeNode
            if (node is IssueNode) {
                tree.rowCount != tree.getRowForPath(it) + 1
            } else {
                node.childCount > 0
            }
        } ?: oldTree.selectionPath?.let {
            val node = it.lastPathComponent as DefaultMutableTreeNode
            if (node is IssueNode) {
                oldTree.rowCount != oldTree.getRowForPath(it) + 1
            } else {
                node.childCount > 0
            }
        } ?: false
    }

    override fun hasPreviousOccurence(): Boolean {
        val path = tree.selectionPath ?: oldTree.selectionPath ?: return false
        val node = path.lastPathComponent as DefaultMutableTreeNode
        return node is IssueNode && !isFirst(node)
    }

    private fun isFirst(node: TreeNode): Boolean {
        val parent = node.parent
        return parent == null || parent.getIndex(node) == 0 && isFirst(parent)
    }

    override fun goNextOccurence(): OccurenceInfo? {
        return tree.selectionPath?.let {
            occurrence(tree, treeBuilder.getNextIssue(it.lastPathComponent as AbstractNode))
        } ?: oldTree.selectionPath?.let {
            occurrence(oldTree, oldTreeBuilder.getNextIssue(it.lastPathComponent as AbstractNode))
        }
    }

    override fun goPreviousOccurence(): OccurenceInfo? {
        return tree.selectionPath?.let {
            occurrence(tree, treeBuilder.getPreviousIssue(it.lastPathComponent as AbstractNode))
        } ?: oldTree.selectionPath?.let {
            occurrence(oldTree, oldTreeBuilder.getPreviousIssue(it.lastPathComponent as AbstractNode))
        }
    }

    override fun getNextOccurenceActionName(): String {
        return "Next Issue"
    }

    override fun getPreviousOccurenceActionName(): String {
        return "Previous Issue"
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
