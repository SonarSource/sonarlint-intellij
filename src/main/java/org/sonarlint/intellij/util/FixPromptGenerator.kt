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
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import java.util.Comparator
import kotlin.math.max
import kotlin.math.min
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.runReadActionSafely
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.filter.FilteredFindings

/**
 * Builds a structured text prompt describing SonarQube findings so users can paste it into an AI coding agent.
 */
object FixPromptGenerator {

    private const val PROMPT_HEADER = """
Please fix the following SonarQube findings in this project. For each finding, apply the appropriate fix while preserving existing behavior, tests, and coding conventions. Explain briefly what you changed for each item.

"""

    private const val PROMPT_FOOTER = """
After applying the fixes, run the relevant tests and static analysis to confirm the findings are resolved.
"""

    fun generate(findings: FilteredFindings): String {
        if (findings.isEmpty()) {
            return ""
        }

        val sections = mutableListOf<String>()
        sections.add(PROMPT_HEADER.trim())

        var index = 1
        findings.issues.sortedWith(liveFindingComparator).forEach { issue ->
            sections.add(formatIssue(index++, issue))
        }
        findings.hotspots.sortedWith(liveFindingComparator).forEach { hotspot ->
            sections.add(formatSecurityHotspot(index++, hotspot))
        }
        findings.taints.forEach { taint ->
            sections.add(formatTaint(index++, taint))
        }
        findings.dependencyRisks.forEach { risk ->
            sections.add(formatDependencyRisk(index++, risk))
        }

        sections.add(PROMPT_FOOTER.trim())
        return sections.joinToString("\n\n")
    }

    fun countFindings(findings: FilteredFindings): Int =
        findings.issues.size + findings.hotspots.size + findings.taints.size + findings.dependencyRisks.size

    private val liveFindingComparator = Comparator<LiveFinding> { a, b ->
        compareValuesBy(a, b, { it.file().path }, { lineNumber(it.file(), it.range) })
    }

    private fun formatIssue(index: Int, issue: LiveIssue): String = buildString {
        appendLine("## Finding $index — Issue")
        appendLine("- **File:** ${issue.file().path}")
        appendLine("- **Location:** ${formatLocation(issue.file(), issue.range)}")
        appendLine("- **Rule:** ${issue.ruleKey}${issue.type?.let { " ($it)" } ?: ""}")
        appendLine("- **Severity:** ${formatLiveFindingSeverity(issue)}")
        appendLine("- **Message:** ${issue.message}")
        appendStatusLines(issue)
        appendQuickFixes(issue)
        appendFlows(issue.context().orElse(null)?.flows())
        appendCodeContext(issue.file(), issue.range)
    }.trimEnd()

    private fun formatSecurityHotspot(index: Int, hotspot: LiveFinding): String = buildString {
        appendLine("## Finding $index — Security Hotspot")
        appendLine("- **File:** ${hotspot.file().path}")
        appendLine("- **Location:** ${formatLocation(hotspot.file(), hotspot.range)}")
        appendLine("- **Rule:** ${hotspot.ruleKey}")
        appendLine("- **Severity:** ${formatLiveFindingSeverity(hotspot)}")
        appendLine("- **Message:** ${hotspot.message}")
        appendStatusLines(hotspot)
        appendQuickFixes(hotspot)
        appendFlows(hotspot.context().orElse(null)?.flows())
        appendCodeContext(hotspot.file(), hotspot.range)
    }.trimEnd()

    private fun formatTaint(index: Int, taint: LocalTaintVulnerability): String = buildString {
        appendLine("## Finding $index — Taint Vulnerability")
        appendLine("- **File:** ${taint.file()?.path ?: "Unknown"}")
        appendLine("- **Location:** ${formatLocation(taint.file(), taint.rangeMarker())}")
        appendLine("- **Rule:** ${taint.ruleKey}")
        appendLine("- **Severity:** ${formatTaintSeverity(taint)}")
        appendLine("- **Message:** ${taint.message()}")
        if (taint.isResolved()) {
            appendLine("- **Status:** Resolved")
        }
        if (taint.isOnNewCode()) {
            appendLine("- **On new code:** Yes")
        }
        appendFlows(taint.flows)
        appendCodeContext(taint.file(), taint.rangeMarker())
    }.trimEnd()

    private fun formatDependencyRisk(index: Int, risk: LocalDependencyRisk): String = buildString {
        appendLine("## Finding $index — Dependency Risk")
        appendLine("- **Package:** ${risk.packageName} ${risk.packageVersion}")
        appendLine("- **Rule:** ${risk.ruleKey}")
        appendLine("- **Type:** ${risk.type}")
        appendLine("- **Severity:** ${risk.severity}")
        appendLine("- **Quality:** ${risk.quality}")
        risk.vulnerabilityId?.let { appendLine("- **Vulnerability ID:** $it") }
        risk.cvssScore?.let { appendLine("- **CVSS score:** $it") }
        appendLine("- **Action:** Upgrade or replace the dependency to a safe version.")
    }.trimEnd()

    private fun StringBuilder.appendStatusLines(finding: LiveFinding) {
        if (finding.isResolved()) {
            appendLine("- **Status:** Resolved")
        }
        if (finding.isOnNewCode()) {
            appendLine("- **On new code:** Yes")
        }
    }

    private fun StringBuilder.appendQuickFixes(finding: LiveFinding) {
        val quickFixes = finding.quickFixes()
        if (quickFixes.isNotEmpty()) {
            appendLine("- **Suggested quick fixes:**")
            quickFixes.forEach { quickFix ->
                appendLine("  - ${quickFix.message}")
            }
        }
    }

    private fun StringBuilder.appendFlows(flows: List<org.sonarlint.intellij.finding.Flow>?) {
        if (flows.isNullOrEmpty()) {
            return
        }
        appendLine("- **Data flows:**")
        flows.forEachIndexed { flowIndex, flow ->
            appendLine("  - Flow ${flowIndex + 1}:")
            flow.locations.forEachIndexed { locationIndex, location ->
                appendLine("    ${locationIndex + 1}. ${formatFlowLocation(location)}")
            }
        }
    }

    private fun StringBuilder.appendCodeContext(file: VirtualFile?, range: RangeMarker?) {
        extractCodeContext(file, range)?.let { context ->
            appendLine("- **Code context:**")
            appendLine("```")
            appendLine(context)
            append("```")
        }
    }

    private fun formatLiveFindingSeverity(finding: LiveFinding): String {
        if (finding.isMqrMode) {
            val attribute = finding.cleanCodeAttribute?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
            val impacts = finding.impacts.joinToString(", ") { "${it.softwareQuality}: ${it.impactSeverity}" }
            return buildString {
                if (attribute != null) {
                    append("Clean code attribute: $attribute")
                }
                if (impacts.isNotEmpty()) {
                    if (isNotEmpty()) append("; ")
                    append("Impacts: $impacts")
                }
                if (isEmpty()) append("Unknown")
            }
        }
        return finding.userSeverity?.name ?: "Unknown"
    }

    private fun formatTaintSeverity(taint: LocalTaintVulnerability): String {
        taint.severity()?.name?.let { return it }
        val attribute = taint.cleanCodeAttribute?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
        val impacts = taint.impacts.joinToString(", ") { "${it.softwareQuality}: ${it.impactSeverity}" }
        return when {
            attribute != null && impacts.isNotEmpty() -> "Clean code attribute: $attribute; Impacts: $impacts"
            attribute != null -> "Clean code attribute: $attribute"
            impacts.isNotEmpty() -> "Impacts: $impacts"
            else -> "Unknown"
        }
    }

    private fun formatLocation(file: VirtualFile?, range: RangeMarker?): String {
        if (range == null) {
            return "Unknown"
        }
        var result = "Unknown"
        runReadActionSafely {
            if (!range.isValid || file == null || !file.isValid) return@runReadActionSafely
            val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return@runReadActionSafely
            val line = document.getLineNumber(range.startOffset) + 1
            val column = range.startOffset - document.getLineStartOffset(line - 1) + 1
            val endLine = document.getLineNumber(range.endOffset) + 1
            result = if (line == endLine) {
                "line $line, column $column"
            } else {
                "lines $line-$endLine, column $column"
            }
        }
        return result
    }

    private fun formatFlowLocation(location: Location): String {
        val coords = formatLocation(location.file, location.range)
        val message = location.message?.takeIf { it.isNotBlank() }
        val filePath = location.file?.path ?: location.originalFileName ?: "Unknown file"
        return if (message != null) {
            "$filePath ($coords) — $message"
        } else {
            "$filePath ($coords)"
        }
    }

    private fun lineNumber(file: VirtualFile?, range: RangeMarker?): Int {
        if (range == null) {
            return Int.MAX_VALUE
        }
        var result = Int.MAX_VALUE
        runReadActionSafely {
            if (!range.isValid || file == null || !file.isValid) return@runReadActionSafely
            val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return@runReadActionSafely
            result = document.getLineNumber(range.startOffset)
        }
        return result
    }

    internal fun extractCodeContext(file: VirtualFile?, range: RangeMarker?, contextLines: Int = 2): String? {
        if (range == null) {
            return null
        }
        var result: String? = null
        runReadActionSafely {
            if (!range.isValid || file == null || !file.isValid) return@runReadActionSafely
            val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return@runReadActionSafely
            val startLine = document.getLineNumber(range.startOffset)
            val endLine = document.getLineNumber(range.endOffset)
            val fromLine = max(0, startLine - contextLines)
            val toLine = min(document.lineCount - 1, endLine + contextLines)
            result = buildString {
                for (line in fromLine..toLine) {
                    val lineStart = document.getLineStartOffset(line)
                    val lineEnd = document.getLineEndOffset(line)
                    appendLine("${line + 1}: ${document.getText(TextRange(lineStart, lineEnd))}")
                }
            }.trimEnd()
        }
        return result
    }
}
