/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import org.sonarlint.intellij.finding.Finding
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality

class LocalDependencyRisk(private val serverDependencyRisk: DependencyRiskDto) : Finding {

    val type: DependencyRiskDto.Type = serverDependencyRisk.type
    val severity: DependencyRiskDto.Severity = serverDependencyRisk.severity
    val quality: DependencyRiskDto.SoftwareQuality = serverDependencyRisk.quality
    var status: DependencyRiskDto.Status = serverDependencyRisk.status
    val packageName: String = serverDependencyRisk.packageName
    val packageVersion: String = serverDependencyRisk.packageVersion
    val vulnerabilityId = serverDependencyRisk.vulnerabilityId
    val cvssScore = serverDependencyRisk.cvssScore
    val transitions: List<DependencyRiskDto.Transition> = serverDependencyRisk.transitions
    private val resolvedStatus = listOf(DependencyRiskDto.Status.SAFE, DependencyRiskDto.Status.ACCEPT, DependencyRiskDto.Status.FIXED)

    fun canChangeStatus(): Boolean {
        return transitions.isNotEmpty()
    }

    override fun getId(): UUID = serverDependencyRisk.id

    override fun getCleanCodeAttribute() = null

    override fun getImpacts(): List<ImpactDto> {
        val impact = when (quality) {
            DependencyRiskDto.SoftwareQuality.SECURITY -> SoftwareQuality.SECURITY
            DependencyRiskDto.SoftwareQuality.RELIABILITY -> SoftwareQuality.RELIABILITY
            DependencyRiskDto.SoftwareQuality.MAINTAINABILITY -> SoftwareQuality.MAINTAINABILITY
        }
        val impactSeverity = when (severity) {
            DependencyRiskDto.Severity.BLOCKER -> ImpactSeverity.BLOCKER
            DependencyRiskDto.Severity.HIGH -> ImpactSeverity.HIGH
            DependencyRiskDto.Severity.MEDIUM -> ImpactSeverity.MEDIUM
            DependencyRiskDto.Severity.LOW -> ImpactSeverity.LOW
            DependencyRiskDto.Severity.INFO -> ImpactSeverity.INFO
        }
        return listOf(ImpactDto(impact, impactSeverity))
    }

    override fun getHighestQuality(): org.sonarsource.sonarlint.core.client.utils.SoftwareQuality {
        return when (quality) {
            DependencyRiskDto.SoftwareQuality.SECURITY -> org.sonarsource.sonarlint.core.client.utils.SoftwareQuality.SECURITY
            DependencyRiskDto.SoftwareQuality.RELIABILITY -> org.sonarsource.sonarlint.core.client.utils.SoftwareQuality.RELIABILITY
            DependencyRiskDto.SoftwareQuality.MAINTAINABILITY -> org.sonarsource.sonarlint.core.client.utils.SoftwareQuality.MAINTAINABILITY
        }
    }

    override fun getHighestImpact(): org.sonarsource.sonarlint.core.client.utils.ImpactSeverity {
        return when (severity) {
            DependencyRiskDto.Severity.BLOCKER -> org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.BLOCKER
            DependencyRiskDto.Severity.HIGH -> org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.HIGH
            DependencyRiskDto.Severity.MEDIUM -> org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.MEDIUM
            DependencyRiskDto.Severity.LOW -> org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.LOW
            DependencyRiskDto.Severity.INFO -> org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.INFO
        }
    }

    override fun getServerKey() = serverDependencyRisk.vulnerabilityId

    override fun getRuleKey() = "dependency-risk:${serverDependencyRisk.type.name.lowercase()}"

    override fun getType() = null

    override fun getRuleDescriptionContextKey() = null

    override fun file() = null

    override fun isValid() = true

    override fun isOnNewCode() = true

    override fun isResolved() = status in resolvedStatus

    override fun isAiCodeFixable() = false
}
