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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import javax.swing.JPanel
import org.sonarlint.intellij.actions.RESTART_ACTION_TEXT
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.actions.RestartBackendAction.Companion.SONARLINT_ERROR_MSG
import org.sonarlint.intellij.actions.SonarConfigureProject
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.issue.risks.LocalDependencyRisk
import org.sonarlint.intellij.ui.CardPanel
import org.sonarlint.intellij.ui.factory.PanelFactory.Companion.centeredLabel
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTree
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTreeUpdater
import org.sonarlint.intellij.ui.tree.CompactTree
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary

private const val ERROR_CARD_ID = "ERROR_CARD"
private const val NO_BINDING_CARD_ID = "NO_BINDING_CARD"
private const val NO_FILTERED_DEPENDENCY_RISKS_CARD_ID = "NO_FILTERED_DEPENDENCY_RISKS_CARD_ID"
private const val INVALID_BINDING_CARD_ID = "INVALID_BINDING_CARD"
private const val NO_ISSUES_CARD_ID = "NO_ISSUES_CARD"
private const val TREE_CARD_ID = "TREE_CARD"
private const val WAITING_FOR_UPDATES_ID = "WAITING_FOR_UPDATES"

class DependencyRiskPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    private val treeSummary = TreeSummary(project, TreeContentKind.ISSUES, false)
    private val tree: DependencyRiskTree
    private val cards: CardPanel

    private val treeUpdater: DependencyRiskTreeUpdater = DependencyRiskTreeUpdater(treeSummary)


    init {
        // todo cards!
        treeUpdater.model.setCompactTree(CompactTree(mapOf(
//            SummaryNode()
        )))

        tree = DependencyRiskTree(treeUpdater)
        val treePanel = JBPanel<DependencyRiskPanel>(VerticalFlowLayout(0, 0))
        treePanel.add(tree)

        cards = initCards(treePanel)
        switchCard() // todo remove?


        val issuesPanel = JPanel(BorderLayout())
        issuesPanel.add(cards.container, BorderLayout.CENTER)
        setContent(issuesPanel)
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

    fun populate(dependencyRisks: MutableList<LocalDependencyRisk>) {
//        TODO("cache goes here")

        // todo expansion restoration
//        val expandedPaths = TreeUtil.collectExpandedPaths(tree)
//        val selectionPath: TreePath? = tree.selectionPath

        treeUpdater.dependencyRisks = dependencyRisks
        switchCard()
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
            treeUpdater.dependencyRisks.isEmpty()-> {
                showCard(if (treeUpdater.updated) NO_ISSUES_CARD_ID else WAITING_FOR_UPDATES_ID)
            }
            else -> {
                if (treeUpdater.filteredDependencyRisks.isEmpty()) {
                    showCard(NO_FILTERED_DEPENDENCY_RISKS_CARD_ID)
                } else {
                    showCard(TREE_CARD_ID)
                }
            }
        }
    }

    private fun showCard(id: String) {
        cards.show(id)
    }
}
