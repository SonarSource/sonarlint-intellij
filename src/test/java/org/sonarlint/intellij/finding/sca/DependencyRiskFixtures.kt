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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

fun aDependencyRisk(status: DependencyRiskDto.Status) = aDependencyRisk(UUID.randomUUID(), status, DependencyRiskDto.Severity.HIGH, listOf(
    DependencyRiskDto.Transition.ACCEPT))

fun aDependencyRisk(transitions: List<DependencyRiskDto.Transition>) = aDependencyRisk(UUID.randomUUID(), DependencyRiskDto.Status.OPEN,
    DependencyRiskDto.Severity.HIGH, transitions)

fun aDependencyRisk(id: UUID, status: DependencyRiskDto.Status, severity: DependencyRiskDto.Severity, transition: List<DependencyRiskDto.Transition>) =
    LocalDependencyRisk(aDependencyRiskDto(status, listOf(), severity, id))

fun aDependencyRiskDto(status: DependencyRiskDto.Status, transition: List<DependencyRiskDto.Transition>,
                       severity: DependencyRiskDto.Severity = DependencyRiskDto.Severity.HIGH, id: UUID = UUID.randomUUID()) =
    DependencyRiskDto(
        id,
        DependencyRiskDto.Type.VULNERABILITY,
        severity,
        DependencyRiskDto.SoftwareQuality.SECURITY,
        status,
        "test-rule-key",
        "High",
        "CVE-1234",
        "7.5",
        transition
    )
