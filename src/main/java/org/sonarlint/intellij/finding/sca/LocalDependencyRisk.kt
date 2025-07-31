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
package org.sonarlint.intellij.finding.sca

import java.util.UUID
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

class LocalDependencyRisk(serverDependencyRisk: DependencyRiskDto) {

    val id: UUID = serverDependencyRisk.id
    val type: DependencyRiskDto.Type = serverDependencyRisk.type
    val severity: DependencyRiskDto.Severity = serverDependencyRisk.severity
    val status: DependencyRiskDto.Status = serverDependencyRisk.status
    val message: String = serverDependencyRisk.packageName
    val transitions: List<DependencyRiskDto.Transition> = serverDependencyRisk.transitions
    var isResolved = serverDependencyRisk.status in listOf(DependencyRiskDto.Status.SAFE, DependencyRiskDto.Status.ACCEPT)

    fun canChangeStatus(): Boolean {
        return transitions.isNotEmpty() && !isResolved
    }

    fun resolve() {
        isResolved = true
    }

}
