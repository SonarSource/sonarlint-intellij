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
package org.sonarlint.intellij.ui.filter

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import org.apache.commons.lang3.Strings
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesCache
import org.sonarlint.intellij.finding.sca.DependencyRisksCache
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk

data class FilterCriteria(
    val severityFilter: SeverityImpactFilter = SeverityImpactFilter.Severity(SeverityFilter.NO_FILTER),
    val statusFilter: StatusFilter = StatusFilter.OPEN,
    val textFilter: String = "",
    val quickFixFilter: Boolean = false,
    val isMqrMode: Boolean = false
)

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
 * Service responsible for filtering all types of findings based on user-defined criteria.
 * 
 * <h3>Design & Architecture:</h3>
 * This service implements a comprehensive filtering system that applies multiple filter types
 * across different finding categories. It follows a clean separation of concerns where filtering 
 * logic is centralized and independent of UI components.
 * 
 * <h3>Filtering Strategy:</h3>
 * Uses a multi-stage filtering approach:
 * - Data Loading: Retrieves raw findings from various caches and holders
 * - Type-Specific Filtering: Applies filters appropriate to each finding type
 * - Unified Criteria: Uses {@link FilterCriteria} for consistent filter application
 * - Result Aggregation: Returns filtered results in a structured {@link FilteredFindings} container
 */
class FindingsFilter(private val project: Project) {

    fun filterAllFindings(file: VirtualFile?, criteria: FilterCriteria): FilteredFindings {
        val rawFindings = loadRawFindings(file)
        
        return FilteredFindings(
            issues = filterIssues(rawFindings.issues, criteria),
            hotspots = filterHotspots(rawFindings.hotspots, criteria),
            taints = filterTaints(rawFindings.taints, criteria),
            dependencyRisks = filterDependencyRisks(rawFindings.dependencyRisks, criteria)
        )
    }

    private fun loadRawFindings(file: VirtualFile?): FilteredFindings {
        val dependencyRisksCache = getService(project, DependencyRisksCache::class.java)

        if (file == null) {
            return FilteredFindings(
                issues = listOf(),
                hotspots = listOf(),
                taints = listOf(),
                dependencyRisks = dependencyRisksCache.dependencyRisks.toList()
            )
        }

        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        val taintCache = getService(project, TaintVulnerabilitiesCache::class.java)
        
        return FilteredFindings(
            issues = onTheFlyFindingsHolder.getIssuesForFile(file).toList(),
            hotspots = onTheFlyFindingsHolder.getSecurityHotspotsForFile(file).toList(),
            taints = taintCache.getTaintVulnerabilitiesForFile(file).toList(),
            dependencyRisks = dependencyRisksCache.dependencyRisks.toList()
        )
    }

    fun filterAllFindings(analysisResult: AnalysisResult?, criteria: FilterCriteria): FilteredFindings {
        if (analysisResult == null) {
            return FilteredFindings(listOf(), listOf(), listOf(), listOf())
        }

        val rawFindings = loadRawFindings(analysisResult)

        return FilteredFindings(
            issues = filterIssues(rawFindings.issues, criteria),
            hotspots = filterHotspots(rawFindings.hotspots, criteria),
            taints = filterTaints(rawFindings.taints, criteria),
            dependencyRisks = filterDependencyRisks(rawFindings.dependencyRisks, criteria)
        )
    }

    private fun loadRawFindings(analysisResult: AnalysisResult): FilteredFindings {
        val findings = analysisResult.findings

        // Flatten the per-file findings into lists
        val allIssues = findings.issuesPerFile.values.flatten()
        val allHotspots = findings.securityHotspotsPerFile.values.flatten()
        
        // Get the set of files that were analyzed in this specific analysis result
        val analyzedFiles = mutableSetOf<VirtualFile>()
        analyzedFiles.addAll(findings.issuesPerFile.keys)
        analyzedFiles.addAll(findings.securityHotspotsPerFile.keys)
        
        // Load taints from their respective caches, filtered by analyzed files
        val taintCache = getService(project, TaintVulnerabilitiesCache::class.java)
        
        // Filter taints to only include those for files that were part of this analysis
        val filteredTaints = taintCache.taintVulnerabilities.filter { taint ->
            taint.file() in analyzedFiles
        }
        
        return FilteredFindings(
            issues = allIssues,
            hotspots = allHotspots,
            taints = filteredTaints,
            dependencyRisks = listOf()
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
        if (criteria.severityFilter.isNoFilter()) return true
        return if (criteria.isMqrMode) {
            Strings.CI.equals(issue.getHighestImpact()?.name, criteria.severityFilter.getPresentableText())
        } else {
            Strings.CI.equals(issue.userSeverity?.name, criteria.severityFilter.getPresentableText())
        }
    }

    private fun filterIssueByText(issue: LiveIssue, criteria: FilterCriteria): Boolean {
        if (criteria.textFilter.isEmpty()) return true
        val filterText = criteria.textFilter.lowercase(Locale.getDefault())
        return Strings.CI.contains(issue.message, filterText)
            || Strings.CI.contains(issue.getRuleKey(), filterText)
            || Strings.CI.contains(issue.file().name, filterText)
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
        return criteria.severityFilter.isNoFilter()
            || hotspot.vulnerabilityProbability.name.equals(criteria.severityFilter.getPresentableText(), ignoreCase = true)
    }

    private fun filterHotspotByText(hotspot: LiveSecurityHotspot, criteria: FilterCriteria): Boolean {
        if (criteria.textFilter.isEmpty()) return true
        val filterText = criteria.textFilter.lowercase(Locale.getDefault())
        return Strings.CI.contains(hotspot.message, filterText)
            || Strings.CI.contains(hotspot.getRuleKey(), filterText)
            || Strings.CI.contains(hotspot.file().name, filterText)
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
        return Strings.CI.contains(taint.getRuleDescriptionContextKey(), filterText)
            || Strings.CI.contains(taint.getRuleKey(), filterText)
            || Strings.CI.contains(taint.file()?.name, filterText)
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
        return criteria.severityFilter.isNoFilter() || risk.severity.name.equals(criteria.severityFilter.getPresentableText(), ignoreCase = true)
    }

    private fun filterDependencyRiskByText(risk: LocalDependencyRisk, criteria: FilterCriteria): Boolean {
        if (criteria.textFilter.isEmpty()) return true
        val filterText = criteria.textFilter.lowercase(Locale.getDefault())
        return Strings.CI.contains(risk.packageName, filterText)
            || Strings.CI.contains(risk.packageVersion, filterText)
            || Strings.CI.contains(risk.vulnerabilityId, filterText)
    }

    private fun filterDependencyRiskByQuickFix(risk: LocalDependencyRisk, criteria: FilterCriteria): Boolean {
        return !criteria.quickFixFilter || risk.isAiCodeFixable()
    }

    private fun filterByStatus(finding: Finding, criteria: FilterCriteria): Boolean {
        return when (criteria.statusFilter) {
            StatusFilter.NO_FILTER -> true
            StatusFilter.OPEN if !finding.isResolved() -> true
            StatusFilter.RESOLVED if finding.isResolved() -> true
            else -> false
        }
    }

}
