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
package org.sonarlint.intellij.ui.report

import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.filter.FilteredFindings

object ReportFilteringUtils {
    
    /**
     * Converts filtered findings to the LiveFindings format expected by tree builders.
     */
    fun convertFilteredFindingsToMap(filteredFindings: FilteredFindings): LiveFindings {
        val issuesPerFile = filteredFindings.issues
            .groupBy { it.file() }
        
        val hotspotsPerFile = filteredFindings.hotspots
            .groupBy { it.file() }

        return LiveFindings(issuesPerFile, hotspotsPerFile)
    }
    
    /**
     * Splits findings into new/old code collections when focus on new code is enabled.
     */
    fun splitFindingsByCodeAge(findings: LiveFindings): SplitFindings {
        val oldHotspots = findings.securityHotspotsPerFile
            .mapValues { (_, hotspots) -> hotspots.filter { !it.isOnNewCode() } }
            .filterValues { it.isNotEmpty() }
            
        val newHotspots = findings.securityHotspotsPerFile
            .mapValues { (_, hotspots) -> hotspots.filter { it.isOnNewCode() } }
            .filterValues { it.isNotEmpty() }
            
        val oldIssues = findings.issuesPerFile
            .mapValues { (_, issues) -> issues.filter { !it.isOnNewCode() } }
            .filterValues { it.isNotEmpty() }
            
        val newIssues = findings.issuesPerFile
            .mapValues { (_, issues) -> issues.filter { it.isOnNewCode() } }
            .filterValues { it.isNotEmpty() }
        
        return SplitFindings(newIssues, oldIssues, newHotspots, oldHotspots)
    }
    
    /**
     * Creates empty collections for when no focus on new code is enabled.
     */
    fun createNoFocusSplit(findings: LiveFindings): SplitFindings {
        return SplitFindings(
            newIssues = findings.issuesPerFile,
            oldIssues = emptyMap(),
            newHotspots = findings.securityHotspotsPerFile,
            oldHotspots = emptyMap()
        )
    }

}

/**
 * Container for findings split by code age (new vs old).
 */
data class SplitFindings(
    val newIssues: Map<VirtualFile, Collection<LiveIssue>>,
    val oldIssues: Map<VirtualFile, Collection<LiveIssue>>,
    val newHotspots: Map<VirtualFile, Collection<LiveSecurityHotspot>>,
    val oldHotspots: Map<VirtualFile, Collection<LiveSecurityHotspot>>
)
