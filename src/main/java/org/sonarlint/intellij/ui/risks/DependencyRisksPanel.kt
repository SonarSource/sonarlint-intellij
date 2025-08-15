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
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.tree.TreePath
import org.sonarlint.intellij.actions.RESTART_ACTION_TEXT
import org.sonarlint.intellij.actions.RefreshDependencyRisksAction
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.actions.RestartBackendAction.Companion.SONARLINT_ERROR_MSG
import org.sonarlint.intellij.actions.SonarConfigureProject
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.finding.sca.DependencyRisksCache
import org.sonarlint.intellij.finding.sca.DependencyRisksDetectionSupport
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.finding.sca.NotSupported
import org.sonarlint.intellij.ui.CardPanel
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.factory.PanelFactory.Companion.centeredLabel
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskResolvedFilter
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTree
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTreeUpdater
import org.sonarlint.intellij.ui.tree.DependencyRiskTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.util.SonarLintActions
import org.sonarlint.intellij.util.runOnPooledThread

private const val ERROR_CARD_ID = "ERROR_CARD"
private const val NOT_SUPPORTED_CARD_ID = "NOT_SUPPORTED_CARD"
private const val NO_FILTERED_ISSUES_CARD_ID = "NO_FILTERED_ISSUES_CARD"
private const val NO_ISSUES_CARD_ID = "NO_ISSUES_CARD"
private const val TREE_CARD_ID = "TREE_CARD"
private const val TOOLBAR_GROUP_ID = "DependencyRisks"

class DependencyRisksPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    private val treeSummary = DependencyRiskTreeSummary(TreeContentKind.DEPENDENCY_RISKS)
    private val tree: DependencyRiskTree
    private val cards: CardPanel
    private val noDependencyRisksPanel = centeredLabel("")
    private val treeUpdater = DependencyRiskTreeUpdater(treeSummary)
    private val sonarConfigureProject = SonarConfigureProject()
    private val notSupportedPanel = centeredLabel("Dependency risks are currently not supported", "Configure Binding", sonarConfigureProject)
    private var status: DependencyRisksDetectionSupport? = null

    init {
        tree = DependencyRiskTree(treeUpdater)
        val treePanel = JBPanel<DependencyRisksPanel>(VerticalFlowLayout(0, 0))
        treePanel.add(tree)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val selectedNode = tree.getSelectedNodes(LocalDependencyRisk::class.java, null)
                if (selectedNode.isNotEmpty()) {
                    runOnPooledThread {
                        getService(BackendService::class.java).openDependencyRiskInBrowser(project, selectedNode.first().id)
                    }
                    return true
                }
                return false
            }
        }.installOn(tree)

        cards = initCards(treePanel)

        val issuesPanel = JPanel(BorderLayout())
        issuesPanel.add(cards.container, BorderLayout.CENTER)
        setContent(issuesPanel)

        val sonarLintActions = SonarLintActions.getInstance()
        setupToolbar(listOf(
            RefreshDependencyRisksAction(),
            sonarLintActions.includeResolvedDependencyRisksAction(),
            sonarLintActions.configure()
        ))
        updateTrees(getService(project, DependencyRisksCache::class.java).dependencyRisks)
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

    private fun initCards(treePanel: JBPanel<DependencyRisksPanel>): CardPanel {
        val cardsPanel = CardPanel()
        cardsPanel.add(
            centeredLabel(SONARLINT_ERROR_MSG, RESTART_ACTION_TEXT, RestartBackendAction()),
            ERROR_CARD_ID
        )
        cardsPanel.add(notSupportedPanel, NOT_SUPPORTED_CARD_ID)
        cardsPanel.add(
            centeredLabel("No dependency risks shown due to the current filtering", "Show Resolved Dependency Risks",
                SonarLintActions.getInstance().includeResolvedDependencyRisksAction()), NO_FILTERED_ISSUES_CARD_ID
        )
        cardsPanel.add(
            ScrollPaneFactory.createScrollPane(treePanel, true),
            TREE_CARD_ID
        )
        cardsPanel.add(noDependencyRisksPanel, NO_ISSUES_CARD_ID)
        return cardsPanel
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

    fun populate(status: DependencyRisksDetectionSupport) {
        this.status = status
        if (status is NotSupported) {
            updateNotSupportedText(status.reason)
        }
        refreshView()
    }

    private fun updateNotSupportedText(newText: String) {
        val text = notSupportedPanel.emptyText
        text.text = newText
            text.appendLine("Configure binding", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
                ActionUtil.invokeAction(
                    sonarConfigureProject,
                    notSupportedPanel,
                    TOOL_WINDOW_ID,
                    null,
                    null
                )
            }
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
        updater.dependencyRisks = dependencyRisks
        tree.showsRootHandles = updater.filteredDependencyRisks.isNotEmpty()
        TreeUtil.restoreExpandedPaths(tree, expandedPaths)
        if (selectionPath != null) {
            TreeUtil.selectPath(tree, selectionPath)
        } else {
            tree.expandRow(0)
        }
    }

    fun allowResolvedDependencyRisks(includeResolved: Boolean) {
        treeUpdater.resolutionFilter = if (includeResolved) DependencyRiskResolvedFilter.ALL else DependencyRiskResolvedFilter.OPEN_ONLY
        switchCard()
    }

    fun refreshView(): Int {
        return switchCard()
    }

    fun switchCard(): Int {
        when {
            !getService(BackendService::class.java).isAlive() -> {
                showCard(ERROR_CARD_ID)
            }
            status is NotSupported -> {
                showCard(NOT_SUPPORTED_CARD_ID)
            }
            treeUpdater.dependencyRisks.isEmpty() -> {
                showNoDependencyRisksLabel()
            }
            else -> {
                if (treeUpdater.filteredDependencyRisks.isEmpty()) {
                    showCard(NO_FILTERED_ISSUES_CARD_ID)
                } else {
                    showCard(TREE_CARD_ID)
                    return treeUpdater.dependencyRisks.count()
                }
            }
        }
        return 0
    }

    private fun showNoDependencyRisksLabel() {
        val productName = try {
            " on ${getService(project, ProjectBindingManager::class.java).serverConnection.productName}"
        } catch (_: InvalidBindingException) {
            ""
        }
        noDependencyRisksPanel.withEmptyText("No dependency risks found in the latest analysis$productName")
        showCard(NO_ISSUES_CARD_ID)
    }

    private fun showCard(id: String) {
        cards.show(id)
    }
}
