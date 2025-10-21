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
import javax.swing.tree.DefaultTreeModel
import org.sonarlint.intellij.ui.icons.SonarLintIcons.backgroundColorsByVulnerabilityProbability
import org.sonarlint.intellij.ui.icons.SonarLintIcons.borderColorsByVulnerabilityProbability
import org.sonarlint.intellij.ui.icons.SonarLintIcons.hotspotTypeWithProbability
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.ui.currentfile.SummaryUiModel
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.ui.nodes.FileNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.FindingTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability

val VULNERABILITY_PROBABILITIES = listOf(VulnerabilityProbability.HIGH, VulnerabilityProbability.MEDIUM, VulnerabilityProbability.LOW)

class SingleFileHotspotTreeModelBuilder(project: Project, isOldHotspots: Boolean) : SingleFileTreeModelBuilder<LiveSecurityHotspot>() {

    var model: DefaultTreeModel
    private var summaryNode: SummaryNode
    private var treeSummary: TreeSummary = FindingTreeSummary(project, TreeContentKind.SECURITY_HOTSPOTS, isOldHotspots).also {
        summaryNode = SummaryNode(it)
    }
    private var currentFile: VirtualFile? = null
    private var latestHotspots = mutableListOf<LiveSecurityHotspot>()
    private var actualFindingsCount: Int = 0

    init {
        model = DefaultTreeModel(summaryNode).apply {
            setRoot(summaryNode)
        }
    }

    override fun numberOfDisplayedFindings(): Int {
        return actualFindingsCount
    }

    override fun getTreeModel(): DefaultTreeModel {
        return model
    }

    override fun isEmpty(): Boolean {
        return latestHotspots.isEmpty()
    }

    override fun performUpdateModelWithScope(file: VirtualFile?, findings: List<LiveSecurityHotspot>, showFileNames: Boolean) {
        latestHotspots = findings.toMutableList()
        currentFile = file

        summaryNode.removeAllChildren()

        val sortedHotspots = when (sortMode) {
            SortMode.IMPACT -> findings.sortedWith(compareByDescending { it.getHighestImpact() })
            SortMode.DATE -> findings.sortedByDescending { it.introductionDate }
            SortMode.RULE_KEY -> findings.sortedBy { it.getRuleKey() }
            else -> findings.sortedBy { it.validTextRange?.startOffset }
        }

        if (showFileNames && sortedHotspots.isNotEmpty()) {
            // Group by file and create file nodes
            val hotspotsByFile = sortedHotspots.groupBy { it.file() }
            val sortedFiles = hotspotsByFile.keys.sortedBy { it.name }
            
            for (fileKey in sortedFiles) {
                val fileHotspots = hotspotsByFile[fileKey] ?: continue
                val fileNode = FileNode(fileKey, true)
                
                for (hotspot in fileHotspots) {
                    fileNode.add(LiveSecurityHotspotNode(hotspot, false)) // false = don't append filename since it's already in parent
                }
                
                summaryNode.add(fileNode)
            }
        } else {
            // Original flat structure - append filename when not grouped by file
            for (hotspot in sortedHotspots) {
                summaryNode.add(LiveSecurityHotspotNode(hotspot, true))
            }
        }

        // Store the actual count of findings, not file nodes
        actualFindingsCount = sortedHotspots.size
        treeSummary.refresh(if (showFileNames) summaryNode.childCount else 1, sortedHotspots.size)
        model.nodeStructureChanged(summaryNode)
    }

    override fun findFindingByKey(key: String): LiveSecurityHotspot? {
        return findHotspotNode(key)?.hotspot
    }

    override fun getSummaryUiModel(): SummaryUiModel {
        val severity = latestHotspots
            .map { it.vulnerabilityProbability }
            .minByOrNull { VULNERABILITY_PROBABILITIES.indexOf(it) }
        return severity?.let {
            SummaryUiModel(
                hotspotTypeWithProbability(it),
                backgroundColorsByVulnerabilityProbability[it],
                borderColorsByVulnerabilityProbability[it]
            )
        } ?: SummaryUiModel()
    }

    override fun setScopeSuffix(suffix: String) {
        (treeSummary as? FindingTreeSummary)?.setScopeSuffix(suffix)
    }

    override fun removeFinding(finding: LiveSecurityHotspot) {
        findHotspotNode(finding.getId().toString())?.let {
            latestHotspots.remove(finding)
            summaryNode.remove(it)
            treeSummary.refresh(1, latestHotspots.size)
            model.nodeStructureChanged(summaryNode)
        }
    }

    private fun findHotspotNode(key: String): LiveSecurityHotspotNode? {
        summaryNode.children().asIterator().forEach {
            val node = it as LiveSecurityHotspotNode
            val issue = node.hotspot
            if (issue.getServerKey() == key || issue.getId().toString() == key) {
                return node
            }
        }
        return null
    }

}
