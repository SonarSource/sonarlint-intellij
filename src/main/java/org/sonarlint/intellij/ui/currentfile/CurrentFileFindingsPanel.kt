package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.TextRangeMatcher
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.FindingDetailsPanel
import org.sonarlint.intellij.ui.FindingKind
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.ui.tree.IssueTree
import org.sonarlint.intellij.ui.tree.SecurityHotspotTree
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTree
import org.sonarlint.intellij.util.runOnPooledThread

open class CurrentFileFindingsPanel(val project: Project) : SimpleToolWindowPanel(false, false), Disposable {

    lateinit var tree: Tree
    lateinit var oldTree: Tree
    lateinit var treeBuilder: SingleFileIssueTreeModelBuilder
    lateinit var oldTreeBuilder: SingleFileIssueTreeModelBuilder
    lateinit var hotspotTree: Tree
    lateinit var oldHotspotTree: Tree
    lateinit var hotspotTreeBuilder: SingleFileHotspotTreeModelBuilder
    lateinit var oldHotspotTreeBuilder: SingleFileHotspotTreeModelBuilder
    lateinit var taintTree: TaintVulnerabilityTree
    lateinit var oldTaintTree: TaintVulnerabilityTree
    lateinit var taintTreeBuilder: SingleFileTaintTreeModelBuilder
    lateinit var oldTaintTreeBuilder: SingleFileTaintTreeModelBuilder
    lateinit var findingDetailsPanel: FindingDetailsPanel
    private var mainToolbar: ActionToolbar? = null

    init {
        createIssuesTree()
        createOldIssuesTree()
        createHotspotsTree()
        createOldHotspotsTree()
        createTaintsTree()
        createOldTaintsTree()
        createFindingDetailsPanel()
        handleListener()
    }

    fun refreshToolbar() {
        mainToolbar?.updateActionsImmediately()
    }

    private fun createFindingDetailsPanel() {
        findingDetailsPanel = FindingDetailsPanel(project, this, FindingKind.ISSUE)
    }

    fun setToolbar(actions: List<AnAction>) {
        mainToolbar?.let {
            it.targetComponent = null
            super.setToolbar(null)
            mainToolbar = null
        }

        mainToolbar = ActionManager.getInstance().createActionToolbar(TOOL_WINDOW_ID, createActionGroup(actions), false)
        mainToolbar?.targetComponent = this
        val box = Box.createHorizontalBox()
        box.add(mainToolbar?.component)
        super.setToolbar(box)
        mainToolbar?.component?.isVisible = true
    }

    private fun createActionGroup(actions: List<AnAction>): ActionGroup {
        val actionGroup = DefaultActionGroup()
        actions.forEach { action -> actionGroup.add(action) }
        return actionGroup
    }

    private fun createIssuesTree() {
        treeBuilder = SingleFileIssueTreeModelBuilder(project, false)
        tree = IssueTree(project, treeBuilder.getTreeModel())
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private fun createOldIssuesTree() {
        oldTreeBuilder = SingleFileIssueTreeModelBuilder(project, true)
        oldTree = IssueTree(project, oldTreeBuilder.getTreeModel())
        oldTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        oldTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private fun createHotspotsTree() {
        hotspotTreeBuilder = SingleFileHotspotTreeModelBuilder(project, false)
        hotspotTree = SecurityHotspotTree(project, hotspotTreeBuilder.getTreeModel())
        hotspotTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        hotspotTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private fun createOldHotspotsTree() {
        oldHotspotTreeBuilder = SingleFileHotspotTreeModelBuilder(project, true)
        oldHotspotTree = SecurityHotspotTree(project, oldHotspotTreeBuilder.getTreeModel())
        oldHotspotTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        oldHotspotTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private fun createTaintsTree() {
        taintTreeBuilder = SingleFileTaintTreeModelBuilder(project, false)
        taintTree = TaintVulnerabilityTree(project, taintTreeBuilder.getUpdater())
        taintTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        taintTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private fun createOldTaintsTree() {
        oldTaintTreeBuilder = SingleFileTaintTreeModelBuilder(project, true)
        oldTaintTree = TaintVulnerabilityTree(project, oldTaintTreeBuilder.getUpdater())
        oldTaintTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
                    getService(project, EditorDecorator::class.java).removeHighlights()
                }
            }
        })
        oldTaintTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    fun setSelectedIssue(issue: LiveIssue) {
        TreeUtil.findNode((tree.model.root as DefaultMutableTreeNode)) { it is IssueNode && it.issue() == issue }?.let { issueNode ->
            tree.selectionPath = null
            tree.addSelectionPath(TreePath(issueNode.path))
        } ?: run {
            TreeUtil.findNode((oldTree.model.root as DefaultMutableTreeNode)) { it is IssueNode && it.issue() == issue }?.let { issueNode ->
                oldTree.selectionPath = null
                oldTree.addSelectionPath(TreePath(issueNode.path))
            } ?: SonarLintConsole.get(project).error("Cannot select issue in the tree")
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

    private fun issueTreeSelectionChanged() {
        if (!tree.isSelectionEmpty) {
            oldTree.clearSelection()
        }
        val selectedIssueNodes = tree.getSelectedNodes(IssueNode::class.java, null)
        if (selectedIssueNodes.size > 0) {
            updateOnSelect(selectedIssueNodes[0].issue())
        } else {
            clearSelection()
        }
    }

    private fun oldIssueTreeSelectionChanged() {
        if (!oldTree.isSelectionEmpty) {
            tree.clearSelection()
        }
        val selectedIssueNodes = oldTree.getSelectedNodes(IssueNode::class.java, null)
        if (selectedIssueNodes.size > 0) {
            updateOnSelect(selectedIssueNodes[0].issue())
        } else {
            clearSelection()
        }
    }

    private fun hotspotTreeSelectionChanged() {
        if (!hotspotTree.isSelectionEmpty) {
            oldHotspotTree.clearSelection()
        }
        val selectedHotspotNodes = hotspotTree.getSelectedNodes(LiveSecurityHotspotNode::class.java, null)
        if (selectedHotspotNodes.size > 0) {
            updateOnSelect(selectedHotspotNodes[0].hotspot)
        } else {
            clearSelection()
        }
    }

    private fun oldHotspotTreeSelectionChanged() {
        if (!oldHotspotTree.isSelectionEmpty) {
            hotspotTree.clearSelection()
        }
        val selectedHotspotNodes = oldHotspotTree.getSelectedNodes(LiveSecurityHotspotNode::class.java, null)
        if (selectedHotspotNodes.size > 0) {
            updateOnSelect(selectedHotspotNodes[0].hotspot)
        } else {
            clearSelection()
        }
    }

    private fun taintTreeSelectionChanged() {
        if (!taintTree.isSelectionEmpty) {
            oldTaintTree.clearSelection()
        }
        val selectedTaintNodes = taintTree.getSelectedNodes(LocalTaintVulnerability::class.java, null)
        if (selectedTaintNodes.size > 0) {
            updateOnSelect(selectedTaintNodes[0])
        } else {
            clearSelection()
        }
    }

    private fun oldTaintTreeSelectionChanged() {
        if (!taintTree.isSelectionEmpty) {
            oldTaintTree.clearSelection()
        }
        val selectedTaintNodes = oldTaintTree.getSelectedNodes(LocalTaintVulnerability::class.java, null)
        if (selectedTaintNodes.size > 0) {
            updateOnSelect(selectedTaintNodes[0])
        } else {
            clearSelection()
        }
    }

    private fun handleListener() {
        tree.addTreeSelectionListener { _ -> this.issueTreeSelectionChanged() }
        oldTree.addTreeSelectionListener { _ -> this.oldIssueTreeSelectionChanged() }
        hotspotTree.addTreeSelectionListener { _ -> this.hotspotTreeSelectionChanged() }
        oldHotspotTree.addTreeSelectionListener { _ -> this.oldHotspotTreeSelectionChanged() }
        taintTree.addTreeSelectionListener { _ -> this.taintTreeSelectionChanged() }
        oldTaintTree.addTreeSelectionListener { _ -> this.oldTaintTreeSelectionChanged() }
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
                SonarLintProjectNotifications.Companion.get(project)
                    .notifyUnableToOpenFinding("The issue could not be detected by SonarQube for IDE in the current code")
                return
            }
            runOnPooledThread(project) {
                val matcher = TextRangeMatcher(project)
                val rangeMarker = computeReadActionSafely(project) {
                    matcher.matchWithCode(showFinding.file, showFinding.textRange, showFinding.codeSnippet)
                }
                if (rangeMarker == null) {
                    SonarLintProjectNotifications.Companion.get(project)
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

    override fun dispose() {
        // Nothing to do
    }

}
