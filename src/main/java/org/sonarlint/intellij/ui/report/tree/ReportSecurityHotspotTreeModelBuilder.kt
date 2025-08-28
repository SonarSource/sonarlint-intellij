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
package org.sonarlint.intellij.ui.report.tree

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.tree.DefaultTreeModel
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.ui.nodes.FileNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.FindingTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind

class ReportSecurityHotspotTreeModelBuilder(project: Project, isOld: Boolean) {

    var sortMode: SortMode = SortMode.DATE
    val model: DefaultTreeModel
    private val summaryNode: SummaryNode
    private val treeSummary = FindingTreeSummary(project, TreeContentKind.SECURITY_HOTSPOTS, isOld)
    private var latestHotspots = mutableListOf<LiveSecurityHotspot>()

    init {
        summaryNode = SummaryNode(treeSummary)
        model = DefaultTreeModel(summaryNode)
        model.setRoot(summaryNode)
    }

    fun updateModel(findings: Map<VirtualFile, Collection<LiveSecurityHotspot>>) {
        // Flatten all hotspots from all files
        val allHotspots = findings.values.flatten()
        latestHotspots = allHotspots.toMutableList()

        // Clear existing model completely
        clear()

        // Group by file first, then sort within each file
        val hotspotsByFile = allHotspots.groupBy { it.file() }
        
        for ((file, fileHotspots) in hotspotsByFile) {
            val sortedFileHotspots = when (sortMode) {
                SortMode.IMPACT -> fileHotspots.sortedWith(compareByDescending { it.vulnerabilityProbability })
                SortMode.DATE -> fileHotspots.sortedByDescending { it.introductionDate }
                SortMode.RULE_KEY -> fileHotspots.sortedBy { it.getRuleKey() }
                SortMode.LINE_NUMBER -> fileHotspots.sortedBy { it.validTextRange?.startOffset ?: Int.MAX_VALUE }
            }

            // Create file node and add sorted hotspots as children
            val fileNode = FileNode(file, true)
            sortedFileHotspots.forEach { hotspot ->
                fileNode.add(LiveSecurityHotspotNode(hotspot, true))
            }
            
            // Add file node to summary - use simple add instead of insertFileNode to avoid conflicts
            summaryNode.add(fileNode)
        }

        // Sort children after all nodes are added
        if (summaryNode.childCount > 0) {
            val children = (0 until summaryNode.childCount).map { summaryNode.getChildAt(it) as FileNode }
            val sortedChildren = children.sortedWith(compareBy { it.file().name })
            summaryNode.removeAllChildren()
            sortedChildren.forEach { summaryNode.add(it) }
        }

        treeSummary.refresh(hotspotsByFile.size, allHotspots.size)
        model.nodeStructureChanged(summaryNode)
    }

    fun isEmpty(): Boolean = latestHotspots.isEmpty()

    fun numberOfDisplayedFindings(): Int {
        // Count total hotspots across all file nodes
        var count = 0
        for (i in 0 until summaryNode.childCount) {
            val fileNode = summaryNode.getChildAt(i) as? FileNode ?: continue
            count += fileNode.childCount
        }
        return count
    }

    fun clear() {
        latestHotspots.clear()
        summaryNode.removeAllChildren()
        treeSummary.refresh(0, 0)
        model.nodeStructureChanged(summaryNode)
    }

}
