/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.finding.tracking

import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.persistence.CachedFindings

class LocalHistoryFindingTracker(private val previousFindings: CachedFindings) {
    private val remainingPreviousIssuesPerFile =
        previousFindings.issuesPerFile.mapValues { (_, issues) -> issues.toMutableList() }
    private val remainingPreviousSecurityHotspotsPerFile =
        previousFindings.securityHotspotsPerFile.mapValues { (_, hotspots) -> hotspots.toMutableList() }

    fun matchWithPreviousIssue(file: VirtualFile, newIssue: LiveIssue) {
        matchWithPreviousFinding(file, remainingPreviousIssuesPerFile, newIssue)
    }

    fun matchWithPreviousSecurityHotspot(file: VirtualFile, newSecurityHotspot: LiveSecurityHotspot) {
        matchWithPreviousFinding(file, remainingPreviousSecurityHotspotsPerFile, newSecurityHotspot)
    }

    private fun matchWithPreviousFinding(
        file: VirtualFile,
        remainingPreviousFindingsPerFile: Map<VirtualFile, MutableList<Trackable>>,
        newFinding: LiveFinding,
    ) {
        if (firstTimeAnalyzed(file)) {
            // no need to match, and we don't know when the finding was introduced
            return
        }
        val remainingPreviousFindings = remainingPreviousFindingsPerFile[file] ?: mutableListOf()
        val previousMatched = matchWithPreviousFinding(remainingPreviousFindings, newFinding)
        if (previousMatched != null) {
            remainingPreviousFindings.remove(previousMatched)
            copyFromPrevious(newFinding, previousMatched)
        } else {
            // we know the user just introduced this finding
            newFinding.introductionDate = System.currentTimeMillis()
        }
    }

    private fun <T : Trackable, L : LiveFinding> matchWithPreviousFinding(baseInput: MutableCollection<T>, rawInput: L): T? {
        if (baseInput.isNotEmpty()) {
            val tracking = Tracker<L, T>().track({ listOf(rawInput) }) { baseInput }
            tracking.matchedRaws[rawInput]?.let { return it }
        }
        return null
    }

    private fun firstTimeAnalyzed(file: VirtualFile) = !previousFindings.alreadyAnalyzedFiles.contains(file)

    companion object {
        /**
         * Previous matched will be either server issue or preexisting local issue.
         */
        private fun <L : LiveFinding> copyFromPrevious(rawMatched: L, previousMatched: Trackable) {
            rawMatched.backendId = previousMatched.id
            rawMatched.introductionDate = previousMatched.introductionDate
            // FIXME should we not reset those fields when unbinding a project?
            rawMatched.serverFindingKey = previousMatched.serverFindingKey
            rawMatched.isResolved = previousMatched.isResolved
        }
    }
}
