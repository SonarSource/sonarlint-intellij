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
package org.sonarlint.intellij.ui.currentfile.tree

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.borderColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.riskSeverity
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.currentfile.SummaryUiModel
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskTreeUpdater
import org.sonarlint.intellij.ui.tree.CompactTreeModel
import org.sonarlint.intellij.ui.tree.FindingTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

val DEPENDENCY_RISK_SEVERITY_ORDER = listOf(
    DependencyRiskDto.Severity.BLOCKER,
    DependencyRiskDto.Severity.HIGH,
    DependencyRiskDto.Severity.MEDIUM,
    DependencyRiskDto.Severity.LOW,
    DependencyRiskDto.Severity.INFO
)

class SingleFileDependencyRiskTreeModelBuilder(project: Project, isOldRisk: Boolean) : SingleFileTreeModelBuilder<LocalDependencyRisk> {

    var summaryNode: SummaryNode
    private var latestRisks = mutableListOf<LocalDependencyRisk>()
    private var currentFile: VirtualFile? = null
    private var treeSummary: TreeSummary = FindingTreeSummary(project, TreeContentKind.DEPENDENCY_RISKS, isOldRisk).also {
        summaryNode = SummaryNode(it)
    }
    private val dependencyRiskTreeUpdater = DependencyRiskTreeUpdater(treeSummary, summaryNode)
    private var sortMode: SortMode = SortMode.DATE

    override fun setSortMode(mode: SortMode) {
        sortMode = mode
    }

    override fun numberOfDisplayedFindings(): Int {
        return dependencyRiskTreeUpdater.getNumberOfDependencyRisks()
    }

    override fun getTreeModel(): CompactTreeModel {
        return dependencyRiskTreeUpdater.model
    }

    fun getUpdater(): DependencyRiskTreeUpdater {
        return dependencyRiskTreeUpdater
    }

    override fun isEmpty(): Boolean {
        return latestRisks.isEmpty()
    }

    override fun updateModel(file: VirtualFile?, findings: List<LocalDependencyRisk>) {
        latestRisks = findings.toMutableList()
        currentFile = file

        val sortedRisks = when (sortMode) {
            SortMode.IMPACT -> findings.sortedWith(compareByDescending { it.getHighestImpact() })
            SortMode.DATE -> findings
            SortMode.RULE_KEY -> findings.sortedBy { it.getRuleKey() }
            else -> findings.sortedWith(compareByDescending<LocalDependencyRisk> { it.severity }.thenByDescending { it.cvssScore }.thenBy { it.packageName })
        }

        val newModel = dependencyRiskTreeUpdater.createCompactTree(sortedRisks)
        dependencyRiskTreeUpdater.model.setCompactTree(newModel)
        treeSummary.refresh(1, sortedRisks.size)
    }

    override fun findFindingByKey(key: String): LocalDependencyRisk? {
        return null
    }

    override fun getSummaryUiModel(): SummaryUiModel {
        return getRiskWithHighestSeverity()?.let {
            val impactSeverity = it.getHighestImpact()
            SummaryUiModel(
                riskSeverity(it.severity),
                backgroundColorsByImpact[impactSeverity],
                borderColorsByImpact[impactSeverity]
            )
        } ?: SummaryUiModel()
    }

    override fun removeFinding(finding: LocalDependencyRisk) {
        if (latestRisks.remove(finding)) {
            updateModel(currentFile, latestRisks)
        }
    }

    private fun getRiskWithHighestSeverity(): LocalDependencyRisk? {
        return latestRisks.minByOrNull { DEPENDENCY_RISK_SEVERITY_ORDER.indexOf(it.severity) }
    }

}
