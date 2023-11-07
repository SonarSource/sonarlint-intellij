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
package org.sonarlint.intellij.finding.issue.vulnerabilities

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService

@Service(Service.Level.PROJECT)
class TaintVulnerabilitiesCache(val project: Project) {
    var taintVulnerabilities: List<LocalTaintVulnerability> = emptyList()

    fun update(taintVulnerabilityIdsToRemove: Set<UUID>, taintVulnerabilitiesToAdd: List<LocalTaintVulnerability>, taintVulnerabilitiesToUpdate: List<LocalTaintVulnerability>) {
        val currentTaintVulnerabilities = taintVulnerabilities.toMutableList()
        currentTaintVulnerabilities.removeAll { taintVulnerabilityIdsToRemove.contains(it.getId()) }
        currentTaintVulnerabilities.addAll(taintVulnerabilitiesToAdd)
        val updatedTaintVulnerabilityKeys = taintVulnerabilitiesToUpdate.map { updatedTaint -> updatedTaint.getServerKey() }
        currentTaintVulnerabilities.removeAll { updatedTaintVulnerabilityKeys.contains(it.getServerKey()) }
        currentTaintVulnerabilities.addAll(taintVulnerabilitiesToUpdate)
        taintVulnerabilities = currentTaintVulnerabilities
    }

    fun remove(taintVulnerabilityToRemove: LocalTaintVulnerability): Boolean {
        val currentTaintVulnerabilities = taintVulnerabilities.toMutableList()
        val removed = currentTaintVulnerabilities.removeIf { currentVulnerability -> currentVulnerability.getServerKey() == taintVulnerabilityToRemove.getServerKey() }
        if (removed) {
            taintVulnerabilities = currentTaintVulnerabilities
        }
        return removed
    }

    fun getTaintVulnerabilitiesForFile(file: VirtualFile) : List<LocalTaintVulnerability> {
        return taintVulnerabilities.filter { it.file() == file }
    }

    fun getFocusAwareCount(): Int {
        val isFocusOnNewCode = getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)
        return taintVulnerabilities.count { !it.isResolved() && (!isFocusOnNewCode || it.isOnNewCode()) }
    }
}
