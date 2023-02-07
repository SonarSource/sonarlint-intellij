/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

class LocalHistoryFindingTracker(previousFindings: CachedFindings) {
    private val remainingPreviousIssuesPerFile =
        previousFindings.issuesPerFile.mapValues { (_, issues) -> issues.toMutableList() }
    private val remainingPreviousSecurityHotspotsPerFile =
        previousFindings.securityHotspotsPerFile.mapValues { (_, hotspots) -> hotspots.toMutableList() }

    fun matchWithPreviousIssue(file: VirtualFile, newIssue: LiveIssue) {
        val remainingPreviousIssues = remainingPreviousIssuesPerFile[file]
        if (!remainingPreviousIssues.isNullOrEmpty()) {
            matchWithPreviousFinding(remainingPreviousIssues, newIssue)
        }
    }

    fun matchWithPreviousSecurityHotspot(file: VirtualFile, newSecurityHotspot: LiveSecurityHotspot) {
        val remainingPreviousSecurityHotspots = remainingPreviousSecurityHotspotsPerFile[file]
        if (!remainingPreviousSecurityHotspots.isNullOrEmpty()) {
            matchWithPreviousFinding(remainingPreviousSecurityHotspots, newSecurityHotspot)
        }
    }

    private fun <T : Trackable, L : LiveFinding> matchWithPreviousFinding(baseInput: MutableCollection<T>, rawInput: L) {
        val tracking = Tracker<L, T>().track({ listOf(rawInput) }) { baseInput }
        if (tracking.matchedRaws.isNotEmpty()) {
            val previousMatched = tracking.matchedRaws[rawInput]
            baseInput.remove(previousMatched)
            copyFromPrevious(rawInput, previousMatched)
        } else {
            // first time seen, even locally
            rawInput.creationDate = System.currentTimeMillis()
        }
    }

    companion object {
        /**
         * Previous matched will be either server issue or preexisting local issue.
         */
        private fun <L : LiveFinding?> copyFromPrevious(rawMatched: L, previousMatched: Trackable?) {
            rawMatched!!.creationDate = previousMatched!!.creationDate
            // FIXME should we not reset those fields when unbinding a project?
            rawMatched.serverFindingKey = previousMatched.serverFindingKey
            rawMatched.isResolved = previousMatched.isResolved
        }
    }
}