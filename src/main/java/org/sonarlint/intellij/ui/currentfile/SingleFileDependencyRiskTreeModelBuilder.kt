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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.borderColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.riskSeverity
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.risks.tree.DependencyRiskResolvedFilter
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

class SingleFileDependencyRiskTreeModelBuilder(private val project: Project, isOldRisk: Boolean) : SingleFileTreeModelBuilder<LocalDependencyRisk> {

    var summaryNode: SummaryNode
    private var includeLocallyResolvedRisks = false
    private var latestRisks = mutableListOf<LocalDependencyRisk>()
    private var currentFile: VirtualFile? = null
    private var treeSummary: TreeSummary = FindingTreeSummary(project, TreeContentKind.DEPENDENCY_RISKS, isOldRisk).also {
        summaryNode = SummaryNode(it)
    }
    private val dependencyRiskTreeUpdater: DependencyRiskTreeUpdater = DependencyRiskTreeUpdater(treeSummary, summaryNode)

    var resolutionFilter: DependencyRiskResolvedFilter = DependencyRiskResolvedFilter.OPEN_ONLY

    private var sortMode: SortMode = SortMode.LINE

    init {
        // Initialize the tree updater's resolution filter to match the builder's filter
        dependencyRiskTreeUpdater.resolutionFilter = resolutionFilter
    }

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

    override fun removeFinding(finding: LocalDependencyRisk) {
        if (latestRisks.remove(finding)) {
            updateModel(currentFile, latestRisks)
        }
    }

    override fun updateModel(file: VirtualFile?, findings: List<LocalDependencyRisk>) {
        latestRisks = findings.toMutableList()
        currentFile = file

        // Apply resolution filtering
        val filteredRisks = findings.filter { resolutionFilter.filter(it) }

        val sortedRisks = when (sortMode) {
            SortMode.IMPACT -> filteredRisks.sortedWith(compareByDescending { it.getHighestImpact() })
            SortMode.DATE -> filteredRisks
            SortMode.RULE_KEY -> filteredRisks.sortedBy { it.getRuleKey() }
            else -> filteredRisks.sortedWith(compareByDescending<LocalDependencyRisk> { it.severity }.thenByDescending { it.cvssScore }.thenBy { it.packageName })
        }

        // Update the tree updater properly to ensure counts are correct
        dependencyRiskTreeUpdater.resolutionFilter = resolutionFilter
        dependencyRiskTreeUpdater.dependencyRisks = sortedRisks
        treeSummary.refresh(1, sortedRisks.size)
    }

    override fun refreshModel() {
        runOnUiThread(project) { updateModel(currentFile, latestRisks) }
    }

    override fun findFindingByKey(key: String): LocalDependencyRisk? {
        return null
    }

    override fun clear() {
        runOnUiThread(project) {
            updateModel(null, emptyList())
        }
    }

    override fun allowResolvedFindings(shouldIncludeResolvedFindings: Boolean) {
        this.includeLocallyResolvedRisks = shouldIncludeResolvedFindings
        this.resolutionFilter = if (shouldIncludeResolvedFindings) DependencyRiskResolvedFilter.ALL else DependencyRiskResolvedFilter.OPEN_ONLY
        // Update the tree updater's resolution filter to ensure counts stay in sync
        dependencyRiskTreeUpdater.resolutionFilter = resolutionFilter
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

    private fun getRiskWithHighestSeverity(): LocalDependencyRisk? {
        return latestRisks.minByOrNull { DEPENDENCY_RISK_SEVERITY_ORDER.indexOf(it.severity) }
    }

}
