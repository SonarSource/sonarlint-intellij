/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.util

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.runReadActionSafely
import org.sonarlint.intellij.finding.Flow
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.finding.QuickFix
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto

object FixPromptBuilder {

    fun build(findings: FilteredFindings, contextLabel: String): String {
        val sections = mutableListOf<String>()
        sections.add(buildHeader(findings, contextLabel))

        var index = 1
        findings.issues.forEach { issue ->
            sections.add(formatIssue(index++, issue))
        }
        findings.hotspots.forEach { hotspot ->
            sections.add(formatSecurityHotspot(index++, hotspot))
        }
        findings.taints.forEach { taint ->
            sections.add(formatTaintVulnerability(index++, taint))
        }
        findings.dependencyRisks.forEach { risk ->
            sections.add(formatDependencyRisk(index++, risk))
        }

        return sections.joinToString("\n\n")
    }

    private fun buildHeader(findings: FilteredFindings, contextLabel: String): String {
        val totalCount = findings.issues.size + findings.hotspots.size + findings.taints.size + findings.dependencyRisks.size
        val summaryParts = buildList {
            if (findings.issues.isNotEmpty()) add("${findings.issues.size} issue(s)")
            if (findings.hotspots.isNotEmpty()) add("${findings.hotspots.size} security hotspot(s)")
            if (findings.taints.isNotEmpty()) add("${findings.taints.size} taint vulnerability(ies)")
            if (findings.dependencyRisks.isNotEmpty()) add("${findings.dependencyRisks.size} dependency risk(s)")
        }

        return buildString {
            appendLine("Please fix all the following SonarQube findings in this project.")
            appendLine("Apply the appropriate fix for each finding while preserving existing behavior and following the project's coding conventions.")
            appendLine("After making changes, ensure the code would pass SonarQube analysis.")
            appendLine()
            appendLine("Context: $contextLabel")
            appendLine("Total findings: $totalCount (${summaryParts.joinToString(", ")})")
        }.trimEnd()
    }

    private fun formatIssue(index: Int, issue: LiveIssue): String {
        return buildString {
            appendLine("---")
            appendLine("## Finding $index")
            appendLine("Category: Code Issue")
            appendLine("File: ${formatFilePath(issue.file())}")
            appendCoordinates(issue.file(), issue.range)
            appendLine("Rule: ${issue.getRuleKey()}")
            appendSeverityDetails(issue)
            issue.getType()?.let { appendLine("Type: $it") }
            appendLine("Message: ${issue.getMessage()}")
            appendLine("On new code: ${if (issue.isOnNewCode()) "Yes" else "No"}")
            appendLine("Status: ${if (issue.isResolved()) "Resolved" else "Open"}")
            issue.getStatus()?.let { appendLine("Resolution status: $it") }
            appendCodeSnippet(issue.file(), issue.range)
            appendFlowLocations(issue.context().orElse(null)?.flows())
            appendQuickFixes(issue.quickFixes())
        }.trimEnd()
    }

    private fun formatSecurityHotspot(index: Int, hotspot: LiveSecurityHotspot): String {
        return buildString {
            appendLine("---")
            appendLine("## Finding $index")
            appendLine("Category: Security Hotspot")
            appendLine("File: ${formatFilePath(hotspot.file())}")
            appendCoordinates(hotspot.file(), hotspot.range)
            appendLine("Rule: ${hotspot.getRuleKey()}")
            appendSeverityDetails(hotspot)
            appendLine("Vulnerability probability: ${hotspot.vulnerabilityProbability}")
            appendLine("Message: ${hotspot.getMessage()}")
            appendLine("On new code: ${if (hotspot.isOnNewCode()) "Yes" else "No"}")
            appendLine("Status: ${if (hotspot.isResolved()) "Resolved" else "Open"}")
            appendCodeSnippet(hotspot.file(), hotspot.range)
            appendFlowLocations(hotspot.context().orElse(null)?.flows())
            appendQuickFixes(hotspot.quickFixes())
        }.trimEnd()
    }

    private fun formatTaintVulnerability(index: Int, taint: LocalTaintVulnerability): String {
        return buildString {
            appendLine("---")
            appendLine("## Finding $index")
            appendLine("Category: Taint Vulnerability")
            appendLine("File: ${formatFilePath(taint.file())}")
            appendCoordinates(taint.file(), taint.rangeMarker())
            appendLine("Rule: ${taint.getRuleKey()}")
            appendSeverityDetails(taint)
            taint.getType()?.let { appendLine("Type: $it") }
            appendLine("Message: ${taint.message()}")
            appendLine("On new code: ${if (taint.isOnNewCode()) "Yes" else "No"}")
            appendLine("Status: ${if (taint.isResolved()) "Resolved" else "Open"}")
            appendCodeSnippet(taint.file(), taint.rangeMarker())
            appendFlowLocations(taint.flows)
        }.trimEnd()
    }

    private fun formatDependencyRisk(index: Int, risk: LocalDependencyRisk): String {
        return buildString {
            appendLine("---")
            appendLine("## Finding $index")
            appendLine("Category: Dependency Risk")
            appendLine("Package: ${risk.packageName}")
            appendLine("Version: ${risk.packageVersion}")
            appendLine("Rule: ${risk.getRuleKey()}")
            appendLine("Type: ${risk.type}")
            appendLine("Severity: ${risk.severity}")
            appendLine("Quality: ${risk.quality}")
            appendLine("Status: ${risk.status}")
            risk.vulnerabilityId?.let { appendLine("Vulnerability ID: $it") }
            risk.cvssScore?.let { appendLine("CVSS score: $it") }
        }.trimEnd()
    }

    private fun StringBuilder.appendSeverityDetails(finding: org.sonarlint.intellij.finding.Finding) {
        if (isMqrMode(finding)) {
            finding.getCleanCodeAttribute()?.let { appendLine("Clean code attribute: $it") }
            finding.getHighestQuality()?.let { appendLine("Highest quality: $it") }
            finding.getHighestImpact()?.let { appendLine("Highest impact: $it") }
            if (finding.getImpacts().isNotEmpty()) {
                appendLine("Impacts: ${formatImpacts(finding.getImpacts())}")
            }
        } else {
            when (finding) {
                is LiveFinding -> finding.getUserSeverity()?.let { appendLine("Severity: $it") }
                is LocalTaintVulnerability -> finding.severity()?.let { appendLine("Severity: $it") }
            }
        }
    }

    private fun StringBuilder.appendCoordinates(file: VirtualFile?, rangeMarker: RangeMarker?) {
        val coordinates = extractCoordinates(file, rangeMarker)
        if (coordinates.line != null) {
            appendLine("Line: ${coordinates.line}")
        }
        if (coordinates.column != null) {
            appendLine("Column: ${coordinates.column}")
        }
    }

    private fun StringBuilder.appendCodeSnippet(file: VirtualFile?, rangeMarker: RangeMarker?) {
        val snippet = extractCodeSnippet(file, rangeMarker) ?: return
        if (snippet.isBlank()) return
        appendLine()
        appendLine("Code at issue location:")
        appendLine("```")
        appendLine(snippet)
        append("```")
    }

    private fun StringBuilder.appendFlowLocations(flows: List<Flow>?) {
        if (flows.isNullOrEmpty()) return
        appendLine()
        appendLine("Additional flow locations:")
        flows.forEach { flow ->
            flow.locations.forEach { location ->
                appendLine("- ${formatLocation(location)}")
            }
        }
    }

    private fun StringBuilder.appendQuickFixes(quickFixes: List<QuickFix>) {
        if (quickFixes.isEmpty()) return
        appendLine()
        appendLine("Suggested quick fix(es):")
        quickFixes.forEach { quickFix ->
            appendLine("- ${quickFix.message}")
        }
    }

    private fun isMqrMode(finding: org.sonarlint.intellij.finding.Finding): Boolean {
        return when (finding) {
            is LiveFinding -> finding.isMqrMode
            is LocalTaintVulnerability -> finding.isMqrMode()
            else -> false
        }
    }

    private fun formatImpacts(impacts: List<ImpactDto>): String {
        return impacts.joinToString(", ") { "${it.softwareQuality} (${it.impactSeverity})" }
    }

    private fun formatLocation(location: Location): String {
        val filePath = formatFilePath(location.file)
        val coordinates = extractCoordinates(location.file, location.range)
        val locationMessage = location.message?.takeIf { it.isNotBlank() }
        val coordinateText = when {
            coordinates.line != null && coordinates.column != null -> "line ${coordinates.line}, column ${coordinates.column}"
            coordinates.line != null -> "line ${coordinates.line}"
            else -> "unknown location"
        }
        return if (locationMessage != null) {
            "$filePath:$coordinateText - $locationMessage"
        } else {
            "$filePath:$coordinateText"
        }
    }

    private fun formatFilePath(file: VirtualFile?): String {
        return file?.path ?: "Unknown file"
    }

    private fun extractCoordinates(file: VirtualFile?, rangeMarker: RangeMarker?): Coordinates {
        var result = Coordinates(null, null)
        runReadActionSafely {
            if (rangeMarker == null || !rangeMarker.isValid || file == null || !file.isValid) return@runReadActionSafely
            val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return@runReadActionSafely
            val line = document.getLineNumber(rangeMarker.startOffset)
            val column = rangeMarker.startOffset - document.getLineStartOffset(line)
            result = Coordinates(line + 1, column + 1)
        }
        return result
    }

    private fun extractCodeSnippet(file: VirtualFile?, rangeMarker: RangeMarker?): String? {
        var snippet: String? = null
        runReadActionSafely {
            if (rangeMarker == null || !rangeMarker.isValid || file == null || !file.isValid) return@runReadActionSafely
            val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return@runReadActionSafely
            val textRange = LiveFinding.toValidTextRange(rangeMarker) ?: return@runReadActionSafely
            snippet = document.getText(textRange).trim()
        }
        return snippet
    }

    private data class Coordinates(val line: Int?, val column: Int?)
}
