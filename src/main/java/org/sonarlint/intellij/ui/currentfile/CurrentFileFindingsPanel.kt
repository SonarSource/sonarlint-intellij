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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.TextRangeMatcher
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.FindingDetailsPanel
import org.sonarlint.intellij.ui.FindingKind
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.currentfile.tree.SingleFileDependencyRiskTreeModelBuilder
import org.sonarlint.intellij.ui.currentfile.tree.SingleFileHotspotTreeModelBuilder
import org.sonarlint.intellij.ui.currentfile.tree.SingleFileIssueTreeModelBuilder
import org.sonarlint.intellij.ui.currentfile.tree.SingleFileTaintTreeModelBuilder
import org.sonarlint.intellij.ui.currentfile.tree.SingleFileTreeModelBuilder
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTree
import org.sonarlint.intellij.ui.tree.IssueTree
import org.sonarlint.intellij.ui.tree.SecurityHotspotTree
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTree
import org.sonarlint.intellij.util.ToolbarUtils
import org.sonarlint.intellij.util.runOnPooledThread

enum class TreeType {
    ISSUES, HOTSPOTS, TAINTS, DEPENDENCY_RISKS
}

data class TreeConfig<T : Finding, B : SingleFileTreeModelBuilder<T>>(
    var tree: Tree,
    var builder: B
)

/**
 * Base panel class for displaying findings in tree structures with common functionality and infrastructure.
 * 
 * <h3>Design & Architecture:</h3>
 * This abstract base class provides the foundation for displaying findings in tree-based UI components.
 * It implements a factory pattern for creating different types of tree configurations and manages the 
 * common infrastructure needed by all findings display panels.
 */
abstract class CurrentFileFindingsPanel(val project: Project) : SimpleToolWindowPanel(false, false), Disposable, DataProvider {

    private lateinit var mainToolbar: ActionToolbar
    lateinit var findingDetailsPanel: FindingDetailsPanel
    val treeConfigs = mutableMapOf<Pair<TreeType, Boolean>, TreeConfig<*, *>>()
    var currentFile: VirtualFile? = null

    fun getTree(type: TreeType, isOld: Boolean): Tree {
        return treeConfigs.filter { it.key.first == type && it.key.second == isOld }.map { it.value.tree }
            .firstOrNull() ?: run {
                createTreeConfigForType(type, isOld).tree
            }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Finding> getBuilder(type: TreeType, isOld: Boolean): SingleFileTreeModelBuilder<T> {
        return treeConfigs.filter { it.key.first == type && it.key.second == isOld }.map { it.value.builder }
            .firstOrNull() as? SingleFileTreeModelBuilder<T> ?: run {
                createTreeConfigForType(type, isOld).builder as SingleFileTreeModelBuilder<T>
            }
    }

    init {
        // Create all trees
        TreeType.values().forEach { type ->
            createTreeConfigForType(type, isOld = false)
            createTreeConfigForType(type, isOld = true)
        }
        createFindingDetailsPanel()
        setupAllListeners()
    }

    private fun createFindingDetailsPanel() {
        findingDetailsPanel = FindingDetailsPanel(project, this, FindingKind.ISSUE)
    }

    fun setToolbarSections(sections: List<List<AnAction>>) {
        mainToolbar = ActionManager.getInstance().createActionToolbar(TOOL_WINDOW_ID, ToolbarUtils.createActionGroupFromSections(sections), false)
        mainToolbar.targetComponent = this
        val box = Box.createHorizontalBox()
        box.add(mainToolbar.component)
        super.setToolbar(box)
        mainToolbar.component.isVisible = true
    }

    private fun createTreeConfigForType(type: TreeType, isOld: Boolean): TreeConfig<*, *> {
        val key = Pair(type, isOld)
        
        val (builder, tree) = when (type) {
            TreeType.ISSUES -> {
                val builder = SingleFileIssueTreeModelBuilder(project, isOld)
                val tree = IssueTree(project, builder.getTreeModel())
                Pair(builder, tree)
            }
            TreeType.HOTSPOTS -> {
                val builder = SingleFileHotspotTreeModelBuilder(project, isOld)
                val tree = SecurityHotspotTree(project, builder.getTreeModel())
                Pair(builder, tree)
            }
            TreeType.TAINTS -> {
                val builder = SingleFileTaintTreeModelBuilder(project, isOld)
                val tree = TaintVulnerabilityTree(project, builder.getUpdater())
                Pair(builder, tree)
            }
            TreeType.DEPENDENCY_RISKS -> {
                val builder = SingleFileDependencyRiskTreeModelBuilder(project, isOld)
                val tree = DependencyRiskTree(builder.getUpdater())

                object : DoubleClickListener() {
                    override fun onDoubleClick(event: MouseEvent): Boolean {
                        val selectedNode = tree.getSelectedNodes(LocalDependencyRisk::class.java, null)
                        if (selectedNode.isNotEmpty()) {
                            runOnPooledThread {
                                getService(BackendService::class.java).openDependencyRiskInBrowser(project, selectedNode.first().getId())
                            }
                            return true
                        }
                        return false
                    }
                }.installOn(tree)

                Pair(builder, tree)
            }
        }
        
        // Common tree setup
        setupTreeCommonProperties(tree)
        
        // Store in config
        val treeConfig = TreeConfig(tree, builder)
        treeConfigs[key] = treeConfig
        
        return treeConfig
    }

    private fun setupTreeCommonProperties(tree: Tree) {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.isVisible = false // Trees should be hidden initially, shown only when they have content
    }

    fun setSelectedFinding(finding: Finding) {
        when (finding) {
            is LiveFinding -> {
                val issueTree = getTree(TreeType.ISSUES, false) as IssueTree
                val oldIssueTree = getTree(TreeType.ISSUES, true) as IssueTree

                TreeUtil.findNode((issueTree.model.root as DefaultMutableTreeNode)) { it is IssueNode && it.issue() == finding }?.let { issueNode ->
                    issueTree.selectionPath = null
                    issueTree.addSelectionPath(TreePath(issueNode.path))
                } ?: run {
                    TreeUtil.findNode((oldIssueTree.model.root as DefaultMutableTreeNode)) { it is IssueNode && it.issue() == finding }?.let { issueNode ->
                        oldIssueTree.selectionPath = null
                        oldIssueTree.addSelectionPath(TreePath(issueNode.path))
                    } ?: SonarLintConsole.get(project).error("Cannot select issue in the tree")
                }
                findingDetailsPanel.show(finding, false)
            }
            is LocalTaintVulnerability -> {
                val taintTree = getTree(TreeType.TAINTS, false) as TaintVulnerabilityTree
                val oldTaintTree = getTree(TreeType.TAINTS, true) as TaintVulnerabilityTree
                taintTree.setSelectedVulnerability(finding)
                oldTaintTree.setSelectedVulnerability(finding)
                findingDetailsPanel.show(finding, false)
            }
            else -> clearSelection()
        }
    }

    fun selectLocationsTab() {
        findingDetailsPanel.selectLocationsTab()
    }

    fun selectRulesTab() {
        findingDetailsPanel.selectRulesTab()
    }

    private fun clearSelection() {
        findingDetailsPanel.clear()
        getService(project, EditorDecorator::class.java).removeHighlights()
    }

    private fun handleTreeSelectionChanged(type: TreeType, isOld: Boolean) {
        val currentTree = getTree(type, isOld)
        val otherTree = getTree(type, !isOld)
        
        // Clear the other tree's selection
        if (!currentTree.isSelectionEmpty) {
            otherTree.clearSelection()
        }
        
        // Handle selection based on tree type
        when (type) {
            TreeType.ISSUES -> {
                val selectedNodes = currentTree.getSelectedNodes(IssueNode::class.java, null)
                if (selectedNodes.isNotEmpty()) {
                    updateOnSelect(selectedNodes[0].issue())
                } else {
                    clearSelection()
                }
            }
            TreeType.HOTSPOTS -> {
                val selectedNodes = currentTree.getSelectedNodes(LiveSecurityHotspotNode::class.java, null)
                if (selectedNodes.isNotEmpty()) {
                    updateOnSelect(selectedNodes[0].hotspot)
                } else {
                    clearSelection()
                }
            }
            TreeType.TAINTS -> {
                val selectedNodes = currentTree.getSelectedNodes(LocalTaintVulnerability::class.java, null)
                if (selectedNodes.isNotEmpty()) {
                    updateOnSelect(selectedNodes[0])
                } else {
                    clearSelection()
                }
            }
            TreeType.DEPENDENCY_RISKS -> {
                // Dependency risk trees just clear selection for now
                clearSelection()
            }
        }
    }

    private fun setupAllListeners() {
        treeConfigs.entries.forEach {
            it.value.tree.addTreeSelectionListener { _ -> handleTreeSelectionChanged(it.key.first, it.key.second) }
        }
    }

    private fun updateOnSelect(liveFinding: LiveFinding) {
        findingDetailsPanel.show(liveFinding, false)
    }

    private fun updateOnSelect(taint: LocalTaintVulnerability) {
        findingDetailsPanel.show(taint, false)
    }

    fun selectAndOpenCodeFixTab(liveFinding: LiveFinding) {
        findingDetailsPanel.show(liveFinding, true)
    }

    fun <T : Finding> updateOnSelect(issue: LiveFinding?, showFinding: ShowFinding<T>) {
        if (issue != null) {
            updateOnSelect(issue)
        } else {
            if (showFinding.codeSnippet == null) {
                SonarLintProjectNotifications.get(project)
                    .notifyUnableToOpenFinding("The issue could not be detected by SonarQube for IDE in the current code")
                return
            }
            runOnPooledThread(project) {
                val matcher = TextRangeMatcher(project)
                val rangeMarker = computeReadActionSafely(project) {
                    matcher.matchWithCode(showFinding.file, showFinding.textRange, showFinding.codeSnippet)
                }
                if (rangeMarker == null) {
                    SonarLintProjectNotifications.get(project)
                        .notifyUnableToOpenFinding("The issue could not be detected by SonarQube for IDE in the current code. Please verify you are in the right branch.")
                    return@runOnPooledThread
                }
                runOnUiThread(project) {
                    findingDetailsPanel.showServerOnlyIssue(
                        showFinding.module, showFinding.file, showFinding.ruleKey, rangeMarker, showFinding.flows,
                        showFinding.flowMessage
                    )
                }
            }
        }
    }

    override fun getData(dataId: String): Any? {
        return when {
            CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> {
                currentFile?.let { arrayOf(it) }
            }
            CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> currentFile
            CommonDataKeys.PROJECT.`is`(dataId) -> project
            else -> null
        }
    }

    override fun dispose() {
        // Nothing to do
    }

}
