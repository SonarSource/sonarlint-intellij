/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.TreeSelectionModel
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.FindingDetailsPanel
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.ui.report.tree.ReportIssueTreeModelBuilder
import org.sonarlint.intellij.ui.report.tree.ReportSecurityHotspotTreeModelBuilder
import org.sonarlint.intellij.ui.report.tree.ReportTaintTreeModelBuilder
import org.sonarlint.intellij.ui.tree.IssueTree
import org.sonarlint.intellij.ui.tree.SecurityHotspotTree
import org.sonarlint.intellij.ui.tree.TreeExpansionStateManager
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTree

/**
 * Manages tree creation, configuration, and interaction for report panels.
 * Consolidates common tree management logic to reduce duplication.
 */
class ReportTreeManager(
    private val project: Project,
    private val findingDetailsPanel: FindingDetailsPanel,
    private val selectionManager: FindingSelectionManager? = null
) {
    companion object {
        private const val TREE_EXPANSION_THRESHOLD = 30
    }
    
    // Issue trees
    val issuesTreeBuilder = ReportIssueTreeModelBuilder(project, isOld = false)
    val oldIssuesTreeBuilder = ReportIssueTreeModelBuilder(project, isOld = true)
    val issuesTree = IssueTree(project, issuesTreeBuilder.model, selectionManager)
    val oldIssuesTree = IssueTree(project, oldIssuesTreeBuilder.model, selectionManager)
    
    // Security hotspot trees  
    val securityHotspotsTreeBuilder = ReportSecurityHotspotTreeModelBuilder(project, isOld = false)
    val oldSecurityHotspotsTreeBuilder = ReportSecurityHotspotTreeModelBuilder(project, isOld = true)
    val securityHotspotsTree = SecurityHotspotTree(project, securityHotspotsTreeBuilder.model)
    val oldSecurityHotspotsTree = SecurityHotspotTree(project, oldSecurityHotspotsTreeBuilder.model)
    
    // Taint vulnerability trees
    val taintsTreeBuilder = ReportTaintTreeModelBuilder(project, isOld = false)
    val oldTaintsTreeBuilder = ReportTaintTreeModelBuilder(project, isOld = true)
    val taintsTree = TaintVulnerabilityTree(project, taintsTreeBuilder.getTreeUpdater())
    val oldTaintsTree = TaintVulnerabilityTree(project, oldTaintsTreeBuilder.getTreeUpdater())
    
    private val allTrees: List<Tree> = listOf(
        issuesTree, oldIssuesTree, 
        securityHotspotsTree, oldSecurityHotspotsTree,
        taintsTree, oldTaintsTree
    )
    
    init {
        setupTreeInteractions()
        setupTreeSelectionListeners()
    }
    
    private fun setupTreeInteractions() {
        allTrees.forEach(::configureTreeInteraction)
    }
    
    private fun configureTreeInteraction(tree: Tree) {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }
    
    private fun setupTreeSelectionListeners() {
        issuesTree.addTreeSelectionListener { 
            handleIssueSelection(issuesTree, getAllOtherTrees(issuesTree))
        }
        oldIssuesTree.addTreeSelectionListener { 
            handleIssueSelection(oldIssuesTree, getAllOtherTrees(oldIssuesTree))
        }
        securityHotspotsTree.addTreeSelectionListener { event -> 
            handleHotspotSelection(event, securityHotspotsTree, getAllOtherTrees(securityHotspotsTree))
        }
        oldSecurityHotspotsTree.addTreeSelectionListener { event -> 
            handleHotspotSelection(event, oldSecurityHotspotsTree, getAllOtherTrees(oldSecurityHotspotsTree))
        }
        taintsTree.addTreeSelectionListener { 
            handleTaintSelection(taintsTree, getAllOtherTrees(taintsTree))
        }
        oldTaintsTree.addTreeSelectionListener { 
            handleTaintSelection(oldTaintsTree, getAllOtherTrees(oldTaintsTree))
        }
    }
    
    private fun getAllOtherTrees(excludeTree: Tree) = 
        allTrees.filter { it != excludeTree }
    
    private fun handleIssueSelection(tree: Tree, treesToClear: List<Tree>) {
        clearOtherTreeSelections(tree, treesToClear)
        val selectedIssueNodes = tree.getSelectedNodes(IssueNode::class.java, null)
        if (selectedIssueNodes.isNotEmpty()) {
            findingDetailsPanel.show(selectedIssueNodes[0].issue(), false)
        } else {
            clearSelection()
        }
    }
    
    private fun handleHotspotSelection(event: TreeSelectionEvent, tree: Tree, treesToClear: List<Tree>) {
        if (event.source is SecurityHotspotTree) {
            clearOtherTreeSelections(tree, treesToClear)
            val selectedHotspotNodes = tree.getSelectedNodes(LiveSecurityHotspotNode::class.java, null)
            if (selectedHotspotNodes.isNotEmpty()) {
                findingDetailsPanel.show(selectedHotspotNodes[0].hotspot, false)
            } else {
                clearSelection()
            }
        }
    }
    
    private fun handleTaintSelection(tree: Tree, treesToClear: List<Tree>) {
        clearOtherTreeSelections(tree, treesToClear)
        val selectedTaintNodes = tree.getSelectedNodes(LocalTaintVulnerability::class.java, null)
        if (selectedTaintNodes.isNotEmpty()) {
            findingDetailsPanel.show(selectedTaintNodes[0], false)
        } else {
            clearSelection()
        }
    }
    
    private fun clearOtherTreeSelections(activeTree: Tree, treesToClear: List<Tree>) {
        if (!activeTree.isSelectionEmpty) {
            treesToClear.forEach { it.clearSelection() }
        }
    }
    
    private fun clearSelection() {
        findingDetailsPanel.clear()
        runOnUiThread(project) {
            getService(project, EditorDecorator::class.java).removeHighlights()
        }
    }
    
    /**
     * Takes a snapshot of the expansion state of file nodes for all trees.
     * This should be called before updating tree models.
     */
    fun takeTreeStateSnapshot(): Map<Tree, Set<String>> {
        return mapOf(
            issuesTree to TreeExpansionStateManager.takeFileNodeExpansionStateSnapshot(issuesTree),
            oldIssuesTree to TreeExpansionStateManager.takeFileNodeExpansionStateSnapshot(oldIssuesTree),
            securityHotspotsTree to TreeExpansionStateManager.takeFileNodeExpansionStateSnapshot(securityHotspotsTree),
            oldSecurityHotspotsTree to TreeExpansionStateManager.takeFileNodeExpansionStateSnapshot(oldSecurityHotspotsTree),
            taintsTree to TreeExpansionStateManager.takeFileNodeExpansionStateSnapshot(taintsTree),
            oldTaintsTree to TreeExpansionStateManager.takeFileNodeExpansionStateSnapshot(oldTaintsTree)
        )
    }
    
    /**
     * Restores the expansion state of file nodes for all trees from a snapshot.
     * Expands everything by default, then collapses file nodes that were previously collapsed.
     */
    fun restoreTreeState(snapshot: Map<Tree, Set<String>>) {
        runOnUiThread(project) {
            // Expand everything by default
            allTrees.forEach { tree ->
                if (tree.isVisible && tree.model.root != null) {
                    TreeUtil.expandAll(tree)
                }
            }
            
            // Then collapse file nodes that were previously collapsed
            snapshot.forEach { (tree, collapsedPaths) ->
                TreeExpansionStateManager.restoreFileNodeExpansionState(tree, collapsedPaths)
            }
        }
    }
    
    fun expandAllTrees() {
        runOnUiThread(project) {
            allTrees.forEach { tree ->
                if (tree.isVisible && tree.model.root != null) {
                    TreeUtil.expandAll(tree)
                }
            }
        }
    }
    
    fun collapseTrees() {
        runOnUiThread(project) {
            allTrees.forEach { tree ->
                if (tree.isVisible && tree.model.root != null) {
                    TreeUtil.collapseAll(tree, 0)
                }
            }
        }
    }
    
    private fun expandTreeIfSmall(tree: Tree, findingsCount: Int) {
        if (findingsCount < TREE_EXPANSION_THRESHOLD) {
            TreeUtil.expandAll(tree)
        } else {
            tree.expandRow(0)
        }
    }
    
    fun configureTreeVisibility(isFocusOnNewCode: Boolean) {
        runOnUiThread(project) {
            allTrees.forEach { it.showsRootHandles = true }

            issuesTree.isVisible = true
            securityHotspotsTree.isVisible = true
            taintsTree.isVisible = true
            oldIssuesTree.isVisible = isFocusOnNewCode
            oldSecurityHotspotsTree.isVisible = isFocusOnNewCode
            oldTaintsTree.isVisible = isFocusOnNewCode
        }
    }
    
    fun clear() {
        issuesTreeBuilder.clear()
        oldIssuesTreeBuilder.clear()
        securityHotspotsTreeBuilder.clear()
        oldSecurityHotspotsTreeBuilder.clear()
        taintsTreeBuilder.clear()
        oldTaintsTreeBuilder.clear()
    }

}
