/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.tools.SimpleActionGroup
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.tree.TreeUtil
import org.sonarlint.intellij.actions.OpenIssueInBrowserAction
import org.sonarlint.intellij.actions.OpenTaintVulnerabilityDocumentationAction
import org.sonarlint.intellij.actions.RefreshTaintVulnerabilitiesAction
import org.sonarlint.intellij.actions.SonarConfigureProject
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.issue.vulnerabilities.FoundTaintVulnerabilities
import org.sonarlint.intellij.issue.vulnerabilities.InvalidBinding
import org.sonarlint.intellij.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.issue.vulnerabilities.NoBinding
import org.sonarlint.intellij.issue.vulnerabilities.TaintVulnerabilitiesStatus
import org.sonarlint.intellij.ui.SonarLintRulePanel
import org.sonarlint.intellij.ui.nodes.AbstractNode
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LocalTaintVulnerabilityNode
import org.sonarlint.intellij.ui.tree.TaintVulnerabilityTree
import org.sonarlint.intellij.ui.tree.TaintVulnerabilityTreeModelBuilder
import org.sonarlint.intellij.util.SonarLintUtils.getService
import java.awt.BorderLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

import java.util.ArrayList


private const val SPLIT_PROPORTION_PROPERTY = "SONARLINT_TAINT_VULNERABILITIES_SPLIT_PROPORTION"
private const val DEFAULT_SPLIT_PROPORTION = 0.65f

private const val NO_BINDING_CARD_ID = "NO_BINDING_CARD"
private const val INVALID_BINDING_CARD_ID = "INVALID_BINDING_CARD"
private const val NO_ISSUES_CARD_ID = "NO_ISSUES_CARD"
private const val TREE_CARD_ID = "TREE_CARD"

private const val TOOLBAR_GROUP_ID = "TaintVulnerabilities"

class TaintVulnerabilitiesPanel(private val project: Project) : SimpleToolWindowPanel(false, true),
  OccurenceNavigator, DataProvider {

  private lateinit var rulePanel: SonarLintRulePanel
  private lateinit var tree: TaintVulnerabilityTree
  private lateinit var treeBuilder: TaintVulnerabilityTreeModelBuilder
  private val cards = CardPanel()

  class CardPanel {
    val container = JPanel()
    private var subPanels = mutableMapOf<String, JComponent>()

    init {
      container.layout = BoxLayout(container, BoxLayout.PAGE_AXIS)
    }

    fun add(panel: JComponent, id: String) {
      panel.isVisible = subPanels.isEmpty()
      panel.alignmentX = 0.5f
      panel.alignmentY = 0.5f
      container.add(panel)
      subPanels[id] = panel
    }

    fun show(id: String) {
      subPanels.values.forEach { it.isVisible = false }
      subPanels[id]!!.isVisible = true
    }
  }

  init {
    cards.add(centeredLabel("The project is not bound to SonarQube/SonarCloud", ActionLink("Configure Binding", SonarConfigureProject())), NO_BINDING_CARD_ID)
    cards.add(centeredLabel("The project binding is invalid", ActionLink("Edit Binding", SonarConfigureProject())), INVALID_BINDING_CARD_ID)
    cards.add(centeredLabel("No vulnerabilities found in currently opened files."), NO_ISSUES_CARD_ID)
    cards.add(createSplitter(
      ScrollPaneFactory.createScrollPane(createTree()),
      createRulePanel()),
      TREE_CARD_ID
    )
    val issuesPanel = JPanel(BorderLayout())
    val globalSettings = getGlobalSettings()
    if (!globalSettings.isTaintVulnerabilitiesTabDisclaimerDismissed) {
      issuesPanel.add(createDisclaimer(), BorderLayout.NORTH)
    }
    issuesPanel.add(cards.container, BorderLayout.CENTER)
    setContent(issuesPanel)
    setupToolbar(listOf(RefreshTaintVulnerabilitiesAction(), OpenTaintVulnerabilityDocumentationAction()))
  }

  private fun centeredLabel(text: String, actionLink: ActionLink? = null): JPanel {
    val labelPanel = JPanel(HorizontalLayout(5))
    labelPanel.add(JLabel(text), HorizontalLayout.CENTER)
    if (actionLink != null) labelPanel.add(actionLink, HorizontalLayout.CENTER)
    return labelPanel
  }

  private fun expandTree() {
    if (tree.getSelectedNode() == null) {
      if (treeBuilder.numberIssues() < 30) {
        TreeUtil.expand(tree, 2)
      } else {
        tree.expandRow(0)
      }
    }
  }

  private fun createDisclaimer(): StripePanel {
    val stripePanel = StripePanel("This tab displays taint vulnerabilities detected by SonarQube or SonarCloud. SonarLint does not detect those issues locally.", Information)
    stripePanel.addAction("Learn more", OpenTaintVulnerabilityDocumentationAction())
    stripePanel.addAction("Dismiss", object : AnAction() {
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
    toolbar.setTargetComponent(this)
    val toolBarBox = Box.createHorizontalBox()
    toolBarBox.add(toolbar.component)
    setToolbar(toolBarBox)
    toolbar.component.isVisible = true
  }

  private fun showCard(id: String) {
    cards.show(id)
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
          showCard(NO_ISSUES_CARD_ID)
          highlighting.removeHighlights()
        } else {
          showCard(TREE_CARD_ID)
          val selectionPath : TreePath? = tree.selectionPath
          val expandedPaths = getExpandedPaths()
          treeBuilder.updateModel(status.byFile)
          tree.selectionPath = selectionPath
          expandedPaths.forEach { tree.expandPath(it) }
          expandTree()
        }
      }
    }
  }
  private fun getExpandedPaths(): List<TreePath> {
    val expanded: MutableList<TreePath> = ArrayList()
    for (i in 0 until tree.rowCount - 1) {
      val currPath: TreePath = tree.getPathForRow(i)
      val nextPath: TreePath = tree.getPathForRow(i + 1)
      if (currPath.isDescendant(nextPath)) {
        expanded.add(currPath)
      }
    }
    return expanded
  }

  fun setSelectedVulnerability(vulnerability: LocalTaintVulnerability) {
    val vulnerabilityNode = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode)
    { it is LocalTaintVulnerabilityNode && it.issue.key() == vulnerability.key() }
      ?: return
    tree.selectionPath = null
    tree.addSelectionPath(TreePath(vulnerabilityNode.path))
  }

  private fun createRulePanel(): JBTabbedPane {
    // Rule panel
    rulePanel = SonarLintRulePanel(project)
    val scrollableRulePanel = ScrollPaneFactory.createScrollPane(
      rulePanel.panel,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )
    scrollableRulePanel.verticalScrollBar.unitIncrement = 10
    val detailsTab = JBTabbedPane()
    detailsTab.addTab("Rule", null, scrollableRulePanel, "Details about the rule")
    return detailsTab
  }

  private fun createSplitter(c1: JComponent, c2: JComponent): JComponent {
    val savedProportion = PropertiesComponent.getInstance(project).getFloat(SPLIT_PROPORTION_PROPERTY, DEFAULT_SPLIT_PROPORTION)
    val splitter = Splitter(false)
    splitter.firstComponent = c1
    splitter.secondComponent = c2
    splitter.proportion = savedProportion
    splitter.setHonorComponentsMinimumSize(true)
    splitter.addPropertyChangeListener(Splitter.PROP_PROPORTION) {
      PropertiesComponent.getInstance(project).setValue(
        SPLIT_PROPORTION_PROPERTY, splitter.proportion.toString()
      )
    }
    return splitter
  }

  private fun updateRulePanelContent() {
    val highlighting = getService(project, EditorDecorator::class.java)
    val issue = tree.getIssueFromSelectedNode()
    if (issue == null) {
      rulePanel.setRuleKey(null)
      highlighting.removeHighlights()
    } else {
      rulePanel.setRuleKey(issue.ruleKey())
      highlighting.highlight(issue)
    }
  }

  private fun createTree(): TaintVulnerabilityTree {
    treeBuilder = TaintVulnerabilityTreeModelBuilder()
    tree = TaintVulnerabilityTree(project, treeBuilder.model)
    tree.addTreeSelectionListener { updateRulePanelContent() }
    tree.addKeyListener(object : KeyAdapter() {
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
    tree.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        if (!e.isTemporary) {
          updateRulePanelContent()
        }
      }
    })
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    return tree
  }

  private fun occurrence(node: IssueNode?): OccurenceInfo? {
    if (node == null) {
      return null
    }
    val path = TreePath(node.path)
    tree.selectionModel.selectionPath = path
    tree.scrollPathToVisible(path)
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
    val path = tree.selectionPath ?: return false
    val node = path.lastPathComponent as DefaultMutableTreeNode
    return if (node is IssueNode) {
      tree.rowCount != tree.getRowForPath(path) + 1
    } else {
      node.childCount > 0
    }
  }

  override fun hasPreviousOccurence(): Boolean {
    val path = tree.selectionPath ?: return false
    val node = path.lastPathComponent as DefaultMutableTreeNode
    return node is IssueNode && !isFirst(node)
  }

  private fun isFirst(node: TreeNode): Boolean {
    val parent = node.parent
    return parent == null || parent.getIndex(node) == 0 && isFirst(parent)
  }

  override fun goNextOccurence(): OccurenceInfo? {
    val path = tree.selectionPath ?: return null
    return occurrence(treeBuilder.getNextIssue(path.lastPathComponent as AbstractNode))
  }

  override fun goPreviousOccurence(): OccurenceInfo? {
    val path = tree.selectionPath ?: return null
    return occurrence(treeBuilder.getPreviousIssue(path.lastPathComponent as AbstractNode))
  }

  override fun getNextOccurenceActionName(): String {
    return "Next Issue"
  }

  override fun getPreviousOccurenceActionName(): String {
    return "Previous Issue"
  }

  override fun getData(dataId: String): Any? {
    return if (OpenIssueInBrowserAction.TAINT_VULNERABILITY_DATA_KEY.`is`(dataId)) {
      tree.getSelectedIssue()
    } else null
  }

}