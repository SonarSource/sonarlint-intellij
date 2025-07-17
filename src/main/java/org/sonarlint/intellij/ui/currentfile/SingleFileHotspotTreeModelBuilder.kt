package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Collections
import javax.swing.tree.DefaultTreeModel
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsByVulnerabilityProbability
import org.sonarlint.intellij.SonarLintIcons.borderColorsByVulnerabilityProbability
import org.sonarlint.intellij.SonarLintIcons.hotspotTypeWithProbability
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability

val VULNERABILITY_PROBABILITIES = listOf(VulnerabilityProbability.HIGH, VulnerabilityProbability.MEDIUM, VulnerabilityProbability.LOW)

class SingleFileHotspotTreeModelBuilder(private val project: Project, isOldHotspots: Boolean) : SingleFileTreeModelBuilder<LiveSecurityHotspot> {

    var currentFilter = SecurityHotspotFilters.DEFAULT_FILTER
    var shouldIncludeResolvedHotspots = false
    var model: DefaultTreeModel
    private var summaryNode: SummaryNode
    private var treeSummary: TreeSummary
    private var currentFile: VirtualFile? = null
    private var latestHotspots = mutableListOf<LiveSecurityHotspot>()
    private var sortMode: SortMode = SortMode.LINE

    init {
        treeSummary = TreeSummary(project, TreeContentKind.SECURITY_HOTSPOTS, isOldHotspots).also {
            summaryNode = SummaryNode(it)
        }
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
        return latestHotspots.isEmpty()
    }

    override fun removeFinding(finding: LiveSecurityHotspot) {
        findHotspotNode(finding.getId().toString())?.let {
            latestHotspots.remove(finding)
            summaryNode.remove(it)
            treeSummary.refresh(1, latestHotspots.size)
            model.nodeStructureChanged(summaryNode)
        }
    }

    override fun updateModel(file: VirtualFile?, hotspots: List<LiveSecurityHotspot>) {
        latestHotspots = hotspots.toMutableList()
        currentFile = file
        ApplicationManager.getApplication().assertIsDispatchThread()

        summaryNode.removeAllChildren()

        val sortedHotspots = when (sortMode) {
            SortMode.IMPACT -> hotspots.sortedWith(compareByDescending<LiveSecurityHotspot> { it.getHighestImpact() })
            SortMode.DATE -> hotspots.sortedByDescending { it.introductionDate }
            SortMode.RULE_KEY -> hotspots.sortedBy { it.getRuleKey() }
            else -> hotspots.sortedBy { it.getValidTextRange()?.startOffset }
        }

        for (hotspot in sortedHotspots) {
            summaryNode.add(LiveSecurityHotspotNode(hotspot, true))
        }

        treeSummary.refresh(1, sortedHotspots.size)
        model.nodeStructureChanged(summaryNode)
    }

    override fun refreshModel() {
        runOnUiThread(project) { updateModel(currentFile, latestHotspots) }
        currentFile?.let {
            getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(Collections.singleton(it))
        }
    }

    override fun findFindingByKey(key: String): LiveSecurityHotspot? {
        return findHotspotNode(key)?.hotspot
    }

    override fun clear() {
        runOnUiThread(project) {
            updateModel(null, emptyList())
        }
    }

    override fun allowResolvedFindings(shouldIncludeResolvedFindings: Boolean) {
        this.shouldIncludeResolvedHotspots = shouldIncludeResolvedFindings
    }

    fun getSummaryUiModel(): SummaryUiModel {
        val severity = latestHotspots
            .map { it.vulnerabilityProbability }
            .minByOrNull { VULNERABILITY_PROBABILITIES.indexOf(it) }
        return severity?.let {
            SummaryUiModel(hotspotTypeWithProbability(it),
                backgroundColorsByVulnerabilityProbability[it],
                borderColorsByVulnerabilityProbability[it])
        } ?: SummaryUiModel()
    }

    fun setSortMode(mode: SortMode) {
        sortMode = mode
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
