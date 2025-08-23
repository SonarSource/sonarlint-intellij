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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Collections
import javax.swing.tree.DefaultTreeModel
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.borderColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.borderColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.getIconForTypeAndSeverity
import org.sonarlint.intellij.SonarLintIcons.impact
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
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

class SingleFileIssueTreeModelBuilder(private val project: Project, isOldIssue: Boolean) : SingleFileTreeModelBuilder<LiveIssue> {

    var model: DefaultTreeModel
    var summaryNode: SummaryNode
    private var includeLocallyResolvedIssues = false
    private var latestIssues = mutableListOf<LiveIssue>()
    private var currentFile: VirtualFile? = null
    private var treeSummary: TreeSummary = FindingTreeSummary(project, TreeContentKind.ISSUES, isOldIssue).also {
        summaryNode = SummaryNode(it)
    }
    private var sortMode: SortMode = SortMode.LINE

    init {
        model = DefaultTreeModel(summaryNode).apply {
            setRoot(summaryNode)
        }
    }

    override fun numberOfDisplayedFindings(): Int {
        return summaryNode.childCount
    }

    override fun getTreeModel(): DefaultTreeModel {
        return model
    }

    override fun isEmpty(): Boolean {
        return latestIssues.isEmpty()
    }

    override fun removeFinding(finding: LiveIssue) {
        findIssueNode(finding.getId().toString())?.let {
            latestIssues.remove(finding)
            summaryNode.remove(it)
            treeSummary.refresh(1, latestIssues.size)
            model.nodeStructureChanged(summaryNode)
        }
    }

    override fun updateModel(file: VirtualFile?, findings: List<LiveIssue>) {
        latestIssues = findings.toMutableList()
        currentFile = file
        ApplicationManager.getApplication().assertIsDispatchThread()

        summaryNode.removeAllChildren()

        val filteredIssues = findings.filter { accept(it) }
        val sortedIssues = when (sortMode) {
            SortMode.IMPACT -> filteredIssues.sortedWith(compareByDescending<LiveIssue> { it.getHighestImpact() })
            SortMode.DATE -> filteredIssues.sortedByDescending { it.introductionDate }
            SortMode.RULE_KEY -> filteredIssues.sortedBy { it.getRuleKey() }
            else -> filteredIssues.sortedBy { it.validTextRange?.startOffset }
        }

        for (issue in sortedIssues) {
            summaryNode.add(IssueNode(issue))
        }

        treeSummary.refresh(1, sortedIssues.size)
        model.nodeStructureChanged(summaryNode)
    }

    override fun refreshModel() {
        runOnUiThread(project) { updateModel(currentFile, latestIssues) }
        currentFile?.let {
            getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(Collections.singleton(it))
        }
    }

    override fun findFindingByKey(key: String): LiveIssue? {
        return findIssueNode(key)?.issue()
    }

    override fun clear() {
        runOnUiThread(project) {
            updateModel(null, emptyList())
        }
    }

    override fun allowResolvedFindings(shouldIncludeResolvedFindings: Boolean) {
        this.includeLocallyResolvedIssues = shouldIncludeResolvedFindings
    }

    override fun setSortMode(mode: SortMode) {
        sortMode = mode
    }

    override fun getSummaryUiModel(): SummaryUiModel {
        return getIssueWithHighestImpact()?.let {
            it.getHighestImpact()?.let { highestImpact -> SummaryUiModel(impact(highestImpact),
                backgroundColorsByImpact[highestImpact],
                borderColorsByImpact[highestImpact])}
        } ?: getIssueWithHighestSeverity()?.let {
            val type = it.getType()
            val severity = it.userSeverity
            if (type != null && severity != null) {
                SummaryUiModel(getIconForTypeAndSeverity(type, severity),
                    backgroundColorsBySeverity[severity],
                    borderColorsBySeverity[severity])
            } else {
                SummaryUiModel()
            }
        } ?: SummaryUiModel()
    }

    private fun getIssueWithHighestSeverity(): LiveIssue? {
        return latestIssues.minByOrNull { SEVERITY_ORDER.indexOf(it.userSeverity) }
    }

    private fun getIssueWithHighestImpact(): LiveIssue? {
        return latestIssues.minByOrNull { IMPACT_ORDER.indexOf(it.getHighestImpact()) }
    }

    private fun findIssueNode(key: String): IssueNode? {
        summaryNode.children().asIterator().forEach {
            val node = it as IssueNode
            val issue = node.issue()
            if (issue.getServerKey() == key || issue.getId().toString() == key) {
                return node
            }
        }
        return null
    }

    private fun accept(issue: LiveIssue): Boolean {
        return (!issue.isResolved() && issue.isValid()) || includeLocallyResolvedIssues
    }

}
