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
import java.util.Locale
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesCache
import org.sonarlint.intellij.finding.sca.DependencyRisksCache
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk

/**
 * Filter criteria for findings
 */
data class FilterCriteria(
    val severityFilter: String = "All",
    val statusFilter: String = "All", 
    val textFilter: String = "",
    val quickFixFilter: Boolean = false,
    val isMqrMode: Boolean = false
)

/**
 * Container for all filtered findings
 */
data class FilteredFindings(
    val issues: List<LiveIssue>,
    val hotspots: List<LiveSecurityHotspot>,
    val taints: List<LocalTaintVulnerability>,
    val dependencyRisks: List<LocalDependencyRisk>
) {
    fun isEmpty(): Boolean = issues.isEmpty() && hotspots.isEmpty() && taints.isEmpty() && dependencyRisks.isEmpty()
    fun isNotEmpty(): Boolean = !isEmpty()
}

/**
 * Service responsible for filtering all types of findings based on user criteria.
 */
class CurrentFileFindingsFilter(private val project: Project) {

    fun filterAllFindings(file: VirtualFile, criteria: FilterCriteria): FilteredFindings {
        val rawFindings = loadRawFindings(file)
        
        return FilteredFindings(
            issues = filterIssues(rawFindings.issues, criteria),
            hotspots = filterHotspots(rawFindings.hotspots, criteria),
            taints = filterTaints(rawFindings.taints, criteria),
            dependencyRisks = filterDependencyRisks(rawFindings.dependencyRisks, criteria)
        )
    }

    private fun loadRawFindings(file: VirtualFile): FilteredFindings {
        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        val taintCache = getService(project, TaintVulnerabilitiesCache::class.java)
        val dependencyRisksCache = getService(project, DependencyRisksCache::class.java)
        
        return FilteredFindings(
            issues = onTheFlyFindingsHolder.getIssuesForFile(file).toList(),
            hotspots = onTheFlyFindingsHolder.getSecurityHotspotsForFile(file).toList(),
            taints = taintCache.getTaintVulnerabilitiesForFile(file).toList(),
            dependencyRisks = dependencyRisksCache.dependencyRisks.toList()
        )
    }
    
    private fun filterIssues(issues: List<LiveIssue>, criteria: FilterCriteria): List<LiveIssue> {
        return issues.filter { issue ->
            filterIssueBySeverity(issue, criteria)
                && filterByStatus(issue, criteria)
                && filterIssueByText(issue, criteria)
                && filterIssueByQuickFix(issue, criteria)
        }
    }

    private fun filterIssueBySeverity(issue: LiveIssue, criteria: FilterCriteria): Boolean {
        if (criteria.severityFilter == "All") return true
        return if (criteria.isMqrMode) {
            issue.getHighestImpact() != null && issue.getHighestImpact()?.name.equals(criteria.severityFilter, ignoreCase = true)
        } else {
            issue.userSeverity != null && issue.userSeverity?.name.equals(criteria.severityFilter, ignoreCase = true)
        }
    }

    private fun filterIssueByText(issue: LiveIssue, criteria: FilterCriteria): Boolean {
        if (criteria.textFilter.isEmpty()) return true
        val filterText = criteria.textFilter.lowercase(Locale.getDefault())
        return (issue.message != null && issue.message.lowercase(Locale.getDefault()).contains(filterText))
            || issue.getRuleKey().lowercase(Locale.getDefault()).contains(filterText)
            || issue.file().name.lowercase(Locale.getDefault()).contains(filterText)
    }

    private fun filterIssueByQuickFix(issue: LiveIssue, criteria: FilterCriteria): Boolean {
        return !criteria.quickFixFilter || issue.quickFixes().isNotEmpty() || issue.isAiCodeFixable()
    }

    private fun filterHotspots(hotspots: List<LiveSecurityHotspot>, criteria: FilterCriteria): List<LiveSecurityHotspot> {
        return hotspots.filter { hotspot ->
            filterHotspotBySeverity(hotspot, criteria)
                && filterByStatus(hotspot, criteria)
                && filterHotspotByText(hotspot, criteria)
                && filterHotspotByQuickFix(hotspot, criteria)
        }
    }

    private fun filterHotspotBySeverity(hotspot: LiveSecurityHotspot, criteria: FilterCriteria): Boolean {
        return criteria.severityFilter == "All" || hotspot.vulnerabilityProbability.name.equals(criteria.severityFilter, ignoreCase = true)
    }

    private fun filterHotspotByText(hotspot: LiveSecurityHotspot, criteria: FilterCriteria): Boolean {
        if (criteria.textFilter.isEmpty()) return true
        val filterText = criteria.textFilter.lowercase(Locale.getDefault())
        return (hotspot.message != null && hotspot.message.lowercase(Locale.getDefault()).contains(filterText))
            || hotspot.getRuleKey().lowercase(Locale.getDefault()).contains(filterText)
            || hotspot.file().name.lowercase(Locale.getDefault()).contains(filterText)
    }

    private fun filterHotspotByQuickFix(hotspot: LiveSecurityHotspot, criteria: FilterCriteria): Boolean {
        return !criteria.quickFixFilter || hotspot.quickFixes().isNotEmpty() || hotspot.isAiCodeFixable()
    }

    private fun filterTaints(taints: List<LocalTaintVulnerability>, criteria: FilterCriteria): List<LocalTaintVulnerability> {
        return taints.filter { taint ->
            filterByStatus(taint, criteria)
                && filterTaintByText(taint, criteria)
                && filterTaintByQuickFix(taint, criteria)
        }
    }

    private fun filterTaintByText(taint: LocalTaintVulnerability, criteria: FilterCriteria): Boolean {
        if (criteria.textFilter.isEmpty()) return true
        val filterText = criteria.textFilter.lowercase(Locale.getDefault())
        return (taint.getRuleDescriptionContextKey() != null && taint.getRuleDescriptionContextKey()!!.lowercase(Locale.getDefault()).contains(filterText))
            || taint.getRuleKey().lowercase(Locale.getDefault()).contains(filterText)
            || (taint.file() != null && taint.file()?.name != null && taint.file()?.name?.lowercase(Locale.getDefault())?.contains(filterText) == true)
    }

    private fun filterTaintByQuickFix(taint: LocalTaintVulnerability, criteria: FilterCriteria): Boolean {
        return !criteria.quickFixFilter || taint.isAiCodeFixable()
    }

    private fun filterDependencyRisks(dependencyRisks: List<LocalDependencyRisk>, criteria: FilterCriteria): List<LocalDependencyRisk> {
        return dependencyRisks.filter { risk ->
            filterByStatus(risk, criteria)
                && filterDependencyRiskByText(risk, criteria)
                && filterDependencyRiskBySeverity(risk, criteria)
                && filterDependencyRiskByQuickFix(risk, criteria)
        }
    }

    private fun filterDependencyRiskBySeverity(risk: LocalDependencyRisk, criteria: FilterCriteria): Boolean {
        return criteria.severityFilter == "All" || risk.severity.name.equals(criteria.severityFilter, ignoreCase = true)
    }

    private fun filterDependencyRiskByText(risk: LocalDependencyRisk, criteria: FilterCriteria): Boolean {
        if (criteria.textFilter.isEmpty()) return true
        val filterText = criteria.textFilter.lowercase(Locale.getDefault())
        return risk.packageName.lowercase(Locale.getDefault()).contains(filterText)
            || risk.packageVersion.lowercase(Locale.getDefault()).contains(filterText)
            || (risk.vulnerabilityId != null && risk.vulnerabilityId.lowercase(Locale.getDefault()).contains(filterText))
    }

    private fun filterDependencyRiskByQuickFix(risk: LocalDependencyRisk, criteria: FilterCriteria): Boolean {
        return !criteria.quickFixFilter || risk.isAiCodeFixable()
    }

    private fun filterByStatus(finding: org.sonarlint.intellij.finding.Finding, criteria: FilterCriteria): Boolean {
        return criteria.statusFilter == "All" || (criteria.statusFilter == "Open" != finding.isResolved())
    }

}
