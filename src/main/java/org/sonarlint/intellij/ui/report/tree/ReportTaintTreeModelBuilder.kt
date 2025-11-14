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
package org.sonarlint.intellij.ui.report.tree

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.filter.FilterSettingsService
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.CompactTreeModel
import org.sonarlint.intellij.ui.tree.FindingTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTreeUpdater

class ReportTaintTreeModelBuilder(project: Project, isOld: Boolean) {

    val model: CompactTreeModel
    var sortMode: SortMode = getService(FilterSettingsService::class.java).getDefaultSortMode()
    private val summaryNode: SummaryNode
    private val treeSummary = FindingTreeSummary(project, TreeContentKind.TAINT_VULNERABILITIES, isOld)
    private var latestTaints = mutableListOf<LocalTaintVulnerability>()
    private val taintVulnerabilityTreeUpdater: TaintVulnerabilityTreeUpdater

    init {
        summaryNode = SummaryNode(treeSummary)
        taintVulnerabilityTreeUpdater = TaintVulnerabilityTreeUpdater(summaryNode)
        model = taintVulnerabilityTreeUpdater.model
    }

    fun getTreeUpdater(): TaintVulnerabilityTreeUpdater {
        return taintVulnerabilityTreeUpdater
    }

    @Synchronized
    fun updateModel(findings: Map<VirtualFile, Collection<LocalTaintVulnerability>>) {
        // Flatten all taints from all files
        val allTaints = findings.values.flatten()
        latestTaints = allTaints.toMutableList()

        // Sort taints according to the current sort mode
        val sortedTaints = when (sortMode) {
            SortMode.IMPACT -> allTaints.sortedWith(compareByDescending { it.getHighestImpact() })
            SortMode.DATE -> allTaints.sortedByDescending { it.creationDate() }
            SortMode.RULE_KEY -> allTaints.sortedBy { it.getRuleKey() }
            SortMode.LINE_NUMBER -> allTaints.sortedBy { it.rangeMarker()?.startOffset ?: Int.MAX_VALUE }
        }

        // Use the taint vulnerability tree updater to create the proper hierarchical structure
        val compactTree = taintVulnerabilityTreeUpdater.createCompactTree(sortedTaints)
        model.setCompactTree(compactTree)
        
        // Update the summary with file count and total taint count
        val fileCount = sortedTaints.filter { it.file() != null }.distinctBy { it.file() }.size
        treeSummary.refresh(fileCount, sortedTaints.size)
        
        // Store taints for the tree updater (needed for selection)
        taintVulnerabilityTreeUpdater.taintVulnerabilities = sortedTaints
    }

    fun isEmpty(): Boolean = latestTaints.isEmpty()

    fun numberOfDisplayedFindings(): Int {
        return taintVulnerabilityTreeUpdater.getNumberOfTaints()
    }

    @Synchronized
    fun clear() {
        latestTaints.clear()
        taintVulnerabilityTreeUpdater.taintVulnerabilities = emptyList()
        val emptyCompactTree = taintVulnerabilityTreeUpdater.createCompactTree(emptyList())
        model.setCompactTree(emptyCompactTree)
        treeSummary.refresh(0, 0)
    }

}
