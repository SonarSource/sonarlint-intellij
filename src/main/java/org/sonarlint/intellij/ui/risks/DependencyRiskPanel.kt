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
package org.sonarlint.intellij.ui.risks

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.util.UUID
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreePath
import org.sonarlint.intellij.actions.RESTART_ACTION_TEXT
import org.sonarlint.intellij.actions.RefreshTaintVulnerabilitiesAction
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.actions.RestartBackendAction.Companion.SONARLINT_ERROR_MSG
import org.sonarlint.intellij.actions.SonarConfigureProject
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.sca.DependencyRisksCache
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.CardPanel
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.factory.PanelFactory.Companion.centeredLabel
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTree
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTreeUpdater
import org.sonarlint.intellij.ui.tree.CompactTree
import org.sonarlint.intellij.ui.tree.DependencyRiskTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.util.SonarLintActions

private const val ERROR_CARD_ID = "ERROR_CARD"
private const val NO_BINDING_CARD_ID = "NO_BINDING_CARD"
private const val INVALID_BINDING_CARD_ID = "INVALID_BINDING_CARD"
private const val NO_ISSUES_CARD_ID = "NO_ISSUES_CARD"
private const val TREE_CARD_ID = "TREE_CARD"
private const val WAITING_FOR_UPDATES_ID = "WAITING_FOR_UPDATES"
private const val TOOLBAR_GROUP_ID = "DependencyRisks"

class DependencyRiskPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    private val treeSummary = DependencyRiskTreeSummary(TreeContentKind.DEPENDENCY_RISKS)
    private val tree: DependencyRiskTree
    private val cards: CardPanel

    private var treeListener: TreeSelectionListener
    private val treeUpdater = DependencyRiskTreeUpdater(treeSummary)


    init {
        // todo cards!
        treeUpdater.model.setCompactTree(CompactTree(mapOf(
//            SummaryNode()
        )))

        tree = DependencyRiskTree(treeUpdater)
        val treePanel = JBPanel<DependencyRiskPanel>(VerticalFlowLayout(0, 0))
        treePanel.add(tree)

        treeListener = TreeSelectionListener {
            val selectedNode = tree.getSelectedNodes(LocalDependencyRisk::class.java, null)
            if (selectedNode.isNotEmpty()) {
                // TODO: Browse to SQ:S
            }
        }

        cards = initCards(treePanel)
        switchCard() // todo remove?

        val issuesPanel = JPanel(BorderLayout())
        issuesPanel.add(cards.container, BorderLayout.CENTER)
        setContent(issuesPanel)

        val sonarLintActions = SonarLintActions.getInstance()
        setupToolbar(listOf(
            RefreshTaintVulnerabilitiesAction(),
            sonarLintActions.configure()
        ))
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

    private fun initCards(treePanel: JBPanel<DependencyRiskPanel>): CardPanel {
        val cardsPanel = CardPanel()
        cardsPanel.add(
            centeredLabel(SONARLINT_ERROR_MSG, RESTART_ACTION_TEXT, RestartBackendAction()),
            ERROR_CARD_ID
        )
        cardsPanel.add(
            centeredLabel("The project is not bound to SonarQube (Server, Cloud)", "Configure Binding", SonarConfigureProject()),
            NO_BINDING_CARD_ID
        )
        cardsPanel.add(
            centeredLabel("The project binding is invalid", "Edit Binding", SonarConfigureProject()),
            INVALID_BINDING_CARD_ID
        )
        cardsPanel.add(
            centeredLabel("No vulnerabilities found in the latest analysis"),
            NO_ISSUES_CARD_ID
        )
        cardsPanel.add(
            centeredLabel("Waiting for analysis updates"),
            WAITING_FOR_UPDATES_ID
        )
        cardsPanel.add(
            ScrollPaneFactory.createScrollPane(treePanel, true),
            TREE_CARD_ID
        )
        // todo NO_FILTERED_DEPENDENCY_RISKS_CARD_ID
        return cardsPanel
    }

    fun changeStatus(risk: LocalDependencyRisk) {
        val cache = getService(project, DependencyRisksCache::class.java)
        val removed = cache.update(risk)
        if (removed) {
            updateTrees(cache.dependencyRisks)
        }
    }

    fun populate(dependencyRisks: List<LocalDependencyRisk>) {
        val cache = getService(project, DependencyRisksCache::class.java)
        cache.dependencyRisks = dependencyRisks
        updateTrees(dependencyRisks)
    }

    fun update(closedDependencyRiskIds: Set<UUID>, addedDependencyRisks: List<LocalDependencyRisk>, updatedDependencyRisks: List<LocalDependencyRisk>) {
        val cache = getService(project, DependencyRisksCache::class.java)
        cache.update(closedDependencyRiskIds, addedDependencyRisks, updatedDependencyRisks)
        updateTrees(cache.dependencyRisks)
    }

    private fun updateTrees(newDependencyRisks: List<LocalDependencyRisk>) {
        runOnUiThread(project) {
            populateSubTree(tree, treeUpdater, newDependencyRisks)
            switchCard()
        }
    }

    private fun populateSubTree(tree: DependencyRiskTree, updater: DependencyRiskTreeUpdater, dependencyRisks: List<LocalDependencyRisk>) {
        val expandedPaths = TreeUtil.collectExpandedPaths(tree)
        val selectionPath: TreePath? = tree.selectionPath
        // Temporarily remove the listener to avoid transient selection events while changing the model
        tree.removeTreeSelectionListener(treeListener)
        try {
            updater.dependencyRisks = dependencyRisks
            tree.showsRootHandles = dependencyRisks.isNotEmpty()
            TreeUtil.restoreExpandedPaths(tree, expandedPaths)
            if (selectionPath != null) {
                TreeUtil.selectPath(tree, selectionPath)
            } else {
                tree.expandRow(0)
            }
        } finally {
            tree.addTreeSelectionListener(treeListener)
        }
    }

    fun switchCard() {
        when {
            !getService(BackendService::class.java).isAlive() -> {
                showCard(ERROR_CARD_ID)
            }
            !getSettingsFor(project).isBound -> {
                showCard(NO_BINDING_CARD_ID)
            }
            !getService(project, ProjectBindingManager::class.java).isBindingValid -> {
                showCard(INVALID_BINDING_CARD_ID)
            }
            treeUpdater.dependencyRisks.isEmpty() -> {
                showCard(if (treeUpdater.updated) NO_ISSUES_CARD_ID else WAITING_FOR_UPDATES_ID)
            }
            else -> {
                showCard(TREE_CARD_ID)
            }
        }
    }

    private fun showCard(id: String) {
        cards.show(id)
    }
}
