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
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.currentfile.SummaryUiModel
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.ui.icons.SonarLintIcons.backgroundColorsByImpact
import org.sonarlint.intellij.ui.icons.SonarLintIcons.backgroundColorsBySeverity
import org.sonarlint.intellij.ui.icons.SonarLintIcons.borderColorsByImpact
import org.sonarlint.intellij.ui.icons.SonarLintIcons.borderColorsBySeverity
import org.sonarlint.intellij.ui.icons.SonarLintIcons.getIconForTypeAndSeverity
import org.sonarlint.intellij.ui.icons.SonarLintIcons.impact
import org.sonarlint.intellij.ui.nodes.FileNode
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.FindingTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity

val SEVERITY_ORDER =
    listOf(IssueSeverity.BLOCKER, IssueSeverity.CRITICAL, IssueSeverity.MAJOR, IssueSeverity.MINOR, IssueSeverity.INFO)
val IMPACT_ORDER = listOf(
    ImpactSeverity.BLOCKER,
    ImpactSeverity.HIGH,
    ImpactSeverity.MEDIUM,
    ImpactSeverity.LOW,
    ImpactSeverity.INFO
)

class SingleFileIssueTreeModelBuilder(project: Project, isOldIssue: Boolean) : SingleFileTreeModelBuilder<LiveIssue>() {

    var model: DefaultTreeModel
    var summaryNode: SummaryNode
    private var latestIssues = mutableListOf<LiveIssue>()
    private var currentFile: VirtualFile? = null
    private var treeSummary: TreeSummary = FindingTreeSummary(project, TreeContentKind.ISSUES, isOldIssue).also {
        summaryNode = SummaryNode(it)
    }
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
        return latestIssues.isEmpty()
    }

    override fun performUpdateModelWithScope(file: VirtualFile?, findings: List<LiveIssue>, showFileNames: Boolean) {
        latestIssues = findings.toMutableList()
        currentFile = file

        summaryNode.removeAllChildren()

        val filteredIssues = findings.filter { accept(it) }
        val sortedIssues = when (sortMode) {
            SortMode.IMPACT -> filteredIssues.sortedWith(compareByDescending { it.getHighestImpact() })
            SortMode.DATE -> filteredIssues.sortedByDescending { it.introductionDate }
            SortMode.RULE_KEY -> filteredIssues.sortedBy { it.getRuleKey() }
            else -> filteredIssues.sortedBy { it.validTextRange?.startOffset }
        }

        if (showFileNames && sortedIssues.isNotEmpty()) {
            // Group by file and create file nodes
            val issuesByFile = sortedIssues.groupBy { it.file() }
            val sortedFiles = issuesByFile.keys.sortedBy { it.name }
            
            for (fileKey in sortedFiles) {
                val fileIssues = issuesByFile[fileKey] ?: continue
                val fileNode = FileNode(fileKey, false)
                
                for (issue in fileIssues) {
                    fileNode.add(IssueNode(issue))
                }
                
                summaryNode.add(fileNode)
            }
        } else {
            // Original flat structure
            for (issue in sortedIssues) {
                summaryNode.add(IssueNode(issue))
            }
        }

        // Store the actual count of findings, not file nodes
        actualFindingsCount = sortedIssues.size
        treeSummary.refresh(if (showFileNames) summaryNode.childCount else 1, sortedIssues.size)
        model.nodeStructureChanged(summaryNode)
    }

    override fun findFindingByKey(key: String): LiveIssue? {
        return findIssueNode(key)?.issue()
    }

    override fun setScopeSuffix(suffix: String) {
        (treeSummary as? FindingTreeSummary)?.setScopeSuffix(suffix)
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
            val severity = it.userSeverity
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

    override fun removeFinding(finding: LiveIssue) {
        findIssueNode(finding.getId().toString())?.let {
            latestIssues.remove(finding)
            summaryNode.remove(it)
            treeSummary.refresh(1, latestIssues.size)
            model.nodeStructureChanged(summaryNode)
        }
    }

    private fun getIssueWithHighestSeverity(): LiveIssue? {
        return latestIssues.minByOrNull { SEVERITY_ORDER.indexOf(it.userSeverity) }
    }

    private fun getIssueWithHighestImpact(): LiveIssue? {
        return latestIssues.minByOrNull { IMPACT_ORDER.indexOf(it.getHighestImpact()) }
    }

    private fun findIssueNode(key: String): IssueNode? {
        summaryNode.children().asIterator().forEach { child ->
            // Handle both cases: when there are FileNodes as children (grouped by file) 
            // and when IssueNodes are direct children
            when (child) {
                is FileNode -> {
                    child.children().asIterator().forEach { fileChild ->
                        if (fileChild is IssueNode) {
                            val issue = fileChild.issue()
                            if (issue.getServerKey() == key || issue.getId().toString() == key) {
                                return fileChild
                            }
                        }
                    }
                }
                is IssueNode -> {
                    val issue = child.issue()
                    if (issue.getServerKey() == key || issue.getId().toString() == key) {
                        return child
                    }
                }
            }
        }
        return null
    }

    private fun accept(issue: LiveIssue): Boolean {
        return issue.isValid()
    }

}
