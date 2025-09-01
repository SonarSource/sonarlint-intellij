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
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.borderColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.borderColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.getIconForTypeAndSeverity
import org.sonarlint.intellij.SonarLintIcons.impact
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.currentfile.SummaryUiModel
import org.sonarlint.intellij.ui.filter.FilterSettingsService
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.CompactTreeModel
import org.sonarlint.intellij.ui.tree.FindingTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTreeUpdater
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity

val TAINT_SEVERITY_ORDER =
    listOf(IssueSeverity.BLOCKER, IssueSeverity.CRITICAL, IssueSeverity.MAJOR, IssueSeverity.MINOR, IssueSeverity.INFO)
val TAINT_IMPACT_ORDER = listOf(
    ImpactSeverity.BLOCKER,
    ImpactSeverity.HIGH,
    ImpactSeverity.MEDIUM,
    ImpactSeverity.LOW,
    ImpactSeverity.INFO
)

class SingleFileTaintTreeModelBuilder(project: Project, isOldIssue: Boolean) : SingleFileTreeModelBuilder<LocalTaintVulnerability> {

    var summaryNode: SummaryNode
    private var latestTaints = mutableListOf<LocalTaintVulnerability>()
    private var currentFile: VirtualFile? = null
    private var treeSummary: TreeSummary = FindingTreeSummary(project, TreeContentKind.TAINT_VULNERABILITIES, isOldIssue).also {
        summaryNode = SummaryNode(it)
    }
    private val taintVulnerabilityTreeUpdater = TaintVulnerabilityTreeUpdater(summaryNode)

    private var sortMode: SortMode = getService(FilterSettingsService::class.java).getDefaultSortMode()

    override fun setSortMode(mode: SortMode) {
        sortMode = mode
    }

    override fun setScopeSuffix(suffix: String) {
        (treeSummary as? FindingTreeSummary)?.setScopeSuffix(suffix)
    }

    override fun numberOfDisplayedFindings(): Int {
        return taintVulnerabilityTreeUpdater.getNumberOfTaints()
    }

    override fun getTreeModel(): CompactTreeModel {
        return taintVulnerabilityTreeUpdater.model
    }

    fun getUpdater(): TaintVulnerabilityTreeUpdater {
        return taintVulnerabilityTreeUpdater
    }

    override fun isEmpty(): Boolean {
        return latestTaints.isEmpty()
    }

    override fun updateModel(file: VirtualFile?, findings: List<LocalTaintVulnerability>) {
        updateModelWithScope(file, findings, false)
    }

    override fun updateModelWithScope(file: VirtualFile?, findings: List<LocalTaintVulnerability>, showFileNames: Boolean) {
        latestTaints = findings.toMutableList()
        currentFile = file

        val sortedTaints = when (sortMode) {
            SortMode.IMPACT -> findings.sortedWith(compareByDescending { it.getHighestImpact() })
            SortMode.DATE -> findings.sortedByDescending { it.creationDate() }
            SortMode.RULE_KEY -> findings.sortedBy { it.getRuleKey() }
            else -> findings.sortedBy { it.rangeMarker()?.startOffset ?: Int.MAX_VALUE }
        }

        val newModel = taintVulnerabilityTreeUpdater.createCompactTree(sortedTaints, showFileNames)
        taintVulnerabilityTreeUpdater.model.setCompactTree(newModel)
        treeSummary.refresh(if (showFileNames) getFileCount(sortedTaints) else 1, sortedTaints.size)
    }

    private fun getFileCount(taints: List<LocalTaintVulnerability>): Int {
        return taints.map { it.file() }.distinct().size
    }

    override fun findFindingByKey(key: String): LocalTaintVulnerability? {
        return null
    }

    override fun getSummaryUiModel(): SummaryUiModel {
        return getIssueWithHighestImpact()?.let {
            it.getHighestImpact()?.let { highestImpact ->
                SummaryUiModel(
                    impact(highestImpact),
                    backgroundColorsByImpact[highestImpact],
                    borderColorsByImpact[highestImpact]
                )
            }
        } ?: getIssueWithHighestSeverity()?.let {
            val type = it.getType()
            val severity = it.severity()
            if (type != null && severity != null) {
                SummaryUiModel(
                    getIconForTypeAndSeverity(type, severity),
                    backgroundColorsBySeverity[severity],
                    borderColorsBySeverity[severity]
                )
            } else {
                SummaryUiModel()
            }
        } ?: SummaryUiModel()
    }

    override fun removeFinding(finding: LocalTaintVulnerability) {
        if (latestTaints.remove(finding)) {
            updateModel(currentFile, latestTaints)
        }
    }

    private fun getIssueWithHighestSeverity(): LocalTaintVulnerability? {
        return latestTaints.minByOrNull { TAINT_SEVERITY_ORDER.indexOf(it.severity()) }
    }

    private fun getIssueWithHighestImpact(): LocalTaintVulnerability? {
        return latestTaints.minByOrNull { TAINT_IMPACT_ORDER.indexOf(it.getHighestImpact()) }
    }

}
