package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.borderColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.borderColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.getIconForTypeAndSeverity
import org.sonarlint.intellij.SonarLintIcons.impact
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.CompactTreeModel
import org.sonarlint.intellij.ui.tree.TreeContentKind
import org.sonarlint.intellij.ui.tree.TreeSummary
import org.sonarlint.intellij.ui.vulnerabilities.tree.TaintVulnerabilityTreeUpdater
import org.sonarlint.intellij.ui.vulnerabilities.tree.filter.FindingFilter
import org.sonarlint.intellij.ui.vulnerabilities.tree.filter.FocusFilter
import org.sonarlint.intellij.ui.vulnerabilities.tree.filter.ResolutionFilter
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

class SingleFileTaintTreeModelBuilder(private val project: Project, isOldIssue: Boolean) : SingleFileTreeModelBuilder<LocalTaintVulnerability> {

    private var includeLocallyResolvedIssues = false
    var summaryNode: SummaryNode
    private var latestTaints = mutableListOf<LocalTaintVulnerability>()
    private var currentFile: VirtualFile? = null
    private var treeSummary: TreeSummary
    private val taintVulnerabilityTreeUpdater: TaintVulnerabilityTreeUpdater

    // Filtering state
    var focusFilter: FocusFilter = FocusFilter.ALL_CODE
    var resolutionFilter: ResolutionFilter = ResolutionFilter.OPEN_ONLY

    private var sortMode: SortMode = SortMode.LINE

    fun setSortMode(mode: SortMode) {
        sortMode = mode
    }

    init {
        treeSummary = TreeSummary(project, TreeContentKind.TAINT_VULNERABILITIES, isOldIssue).also {
            summaryNode = SummaryNode(it)
        }
        taintVulnerabilityTreeUpdater = TaintVulnerabilityTreeUpdater(treeSummary, summaryNode)
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

    override fun removeFinding(finding: LocalTaintVulnerability) {
        if (latestTaints.remove(finding)) {
            updateModel(currentFile, latestTaints)
        }
    }

    override fun updateModel(file: VirtualFile?, findings: List<LocalTaintVulnerability>) {
        latestTaints = findings.toMutableList()
        currentFile = file

        // Filtering logic (focus, resolution)
        val filters = listOf<FindingFilter>(focusFilter, resolutionFilter)
        val filteredTaints = findings.filter { taint -> filters.all { it.filter(taint) } }

        val sortedTaints = when (sortMode) {
            SortMode.IMPACT -> filteredTaints.sortedWith(compareByDescending<LocalTaintVulnerability> { it.getHighestImpact() })
            SortMode.DATE -> filteredTaints.sortedByDescending { it.creationDate() }
            SortMode.RULE_KEY -> filteredTaints.sortedBy { it.getRuleKey() }
            else -> filteredTaints.sortedBy { it.rangeMarker()?.startOffset ?: Int.MAX_VALUE }
        }

        val newModel = taintVulnerabilityTreeUpdater.createCompactTree(sortedTaints)
        taintVulnerabilityTreeUpdater.model.setCompactTree(newModel)
        treeSummary.refresh(1, sortedTaints.size)
    }

    override fun refreshModel() {
        runOnUiThread(project) { updateModel(currentFile, latestTaints) }
    }

    override fun findFindingByKey(key: String): LocalTaintVulnerability? {
        return null
    }

    override fun clear() {
        runOnUiThread(project) {
            updateModel(null, emptyList())
        }
    }

    override fun allowResolvedFindings(shouldIncludeResolvedFindings: Boolean) {
        this.includeLocallyResolvedIssues = shouldIncludeResolvedFindings
        this.resolutionFilter = if (shouldIncludeResolvedFindings) ResolutionFilter.ALL else ResolutionFilter.OPEN_ONLY
    }

    fun getSummaryUiModel(): SummaryUiModel {
        return getIssueWithHighestImpact()?.let {
            it.getHighestImpact()?.let { highestImpact -> SummaryUiModel(impact(highestImpact),
                backgroundColorsByImpact[highestImpact],
                borderColorsByImpact[highestImpact])}
        } ?: getIssueWithHighestSeverity()?.let {
            val type = it.getType()
            val severity = it.severity()
            if (type != null && severity != null) {
                SummaryUiModel(getIconForTypeAndSeverity(type, severity),
                    backgroundColorsBySeverity[severity],
                    borderColorsBySeverity[severity])
            } else {
                SummaryUiModel()
            }
        } ?: SummaryUiModel()
    }

    private fun getIssueWithHighestSeverity(): LocalTaintVulnerability? {
        return latestTaints.minByOrNull { TAINT_SEVERITY_ORDER.indexOf(it.severity()) }
    }

    private fun getIssueWithHighestImpact(): LocalTaintVulnerability? {
        return latestTaints.minByOrNull { TAINT_IMPACT_ORDER.indexOf(it.getHighestImpact()) }
    }

}
