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
package org.sonarlint.intellij.ui.risks.tree

import com.intellij.openapi.application.ModalityState
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.CompactTree
import org.sonarlint.intellij.ui.tree.CompactTreeModel
import org.sonarlint.intellij.ui.tree.NodeRenderer
import org.sonarlint.intellij.ui.tree.TreeSummary

class DependencyRiskTreeUpdater(private val treeSummary: TreeSummary) {
    var updated = false
    val model = CompactTreeModel(SummaryNode(treeSummary))

    val renderer = NodeRenderer<Any> { renderer, node ->
        when (node) {
            is SummaryNode -> node.render(renderer)
            is LocalDependencyRisk -> {
                LocalDependencyRiskRenderer.render(renderer, node)
            }
        }
    }

    var dependencyRisks: List<LocalDependencyRisk> = mutableListOf()
        set(value) {
            field = value
            runOnUiThread(ModalityState.defaultModalityState()) { model.setCompactTree(createCompactTree(dependencyRisks)) }
            treeSummary.refresh(0, value.size)
        }

    private fun createCompactTree(dependencyRisks: List<LocalDependencyRisk>): CompactTree {
        val sortedDependencyRisks =
            dependencyRisks.sortedWith(compareByDescending<LocalDependencyRisk> { it.severity }.thenBy { it.packageName })
        val nodes: MutableMap<Any, List<Any>> = mutableMapOf(model.root to sortedDependencyRisks)
        return CompactTree(nodes)
    }

}
