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
package org.sonarlint.intellij.finding

import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue

class LiveFindings(
    val issuesPerFile: Map<VirtualFile, Collection<LiveIssue>>,
    val securityHotspotsPerFile: Map<VirtualFile, Collection<LiveSecurityHotspot>>,
) {
    val filesInvolved = issuesPerFile.keys + securityHotspotsPerFile.keys

    fun onlyFor(files: Set<VirtualFile>): LiveFindings {
        return LiveFindings(
            issuesPerFile.filterKeys { it in files },
            securityHotspotsPerFile.filterKeys { it in files })
    }

    fun merge(other: LiveFindings?): LiveFindings {
        other ?: return this
        val mergedIssuesPerFile = issuesPerFile.toMutableMap()
        other.issuesPerFile.forEach { (file, issues) ->
            mergedIssuesPerFile.merge(file, issues) { oldIssues, newIssues -> oldIssues + newIssues }
        }

        val mergedSecurityHotspotsPerFile = securityHotspotsPerFile.toMutableMap()
        other.securityHotspotsPerFile.forEach { (file, hotspots) ->
            mergedSecurityHotspotsPerFile.merge(file, hotspots) { oldHotspots, newHotspots -> oldHotspots + newHotspots }
        }

        return LiveFindings(mergedIssuesPerFile, mergedSecurityHotspotsPerFile)
    }

    companion object {
        @JvmStatic
        fun none() : LiveFindings {
            return LiveFindings(emptyMap(), emptyMap())
        }
    }
}
