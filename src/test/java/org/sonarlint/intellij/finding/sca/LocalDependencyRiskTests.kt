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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

class LocalDependencyRiskTests {

    @Test
    fun `should properly initialize from DependencyRiskDto`() {
        val dto = aDependencyRiskDto(DependencyRiskDto.Status.OPEN, listOf(DependencyRiskDto.Transition.CONFIRM))

        val localRisk = LocalDependencyRisk(dto)

        assertThat(localRisk.getId()).isEqualTo(dto.id)
        assertThat(localRisk.type).isEqualTo(dto.type)
        assertThat(localRisk.severity).isEqualTo(dto.severity)
        assertThat(localRisk.status).isEqualTo(dto.status)
        assertThat(localRisk.transitions).containsExactly(DependencyRiskDto.Transition.CONFIRM)
    }

    @Test
    fun `should be unresolved when status is OPEN`() {
        val dto = aDependencyRiskDto(DependencyRiskDto.Status.OPEN, listOf())
        val localRisk = LocalDependencyRisk(dto)

        assertThat(localRisk.isResolved()).isFalse()
    }

    @Test
    fun `should be resolved when status is SAFE`() {
        val dto = aDependencyRiskDto(DependencyRiskDto.Status.SAFE, listOf())
        val localRisk = LocalDependencyRisk(dto)

        assertThat(localRisk.isResolved()).isTrue()
    }

    @Test
    fun `should be resolved when status is ACCEPT`() {
        val dto = aDependencyRiskDto(DependencyRiskDto.Status.ACCEPT, listOf())
        val localRisk = LocalDependencyRisk(dto)

        assertThat(localRisk.isResolved()).isTrue()
    }

    @Test
    fun `should allow status change when transitions are available and not resolved`() {
        val dto = aDependencyRiskDto(DependencyRiskDto.Status.OPEN, listOf(DependencyRiskDto.Transition.CONFIRM))
        val localRisk = LocalDependencyRisk(dto)

        assertThat(localRisk.canChangeStatus()).isTrue()
    }

    @Test
    fun `should not allow status change when no transitions available`() {
        val dto = aDependencyRiskDto(DependencyRiskDto.Status.OPEN, listOf())
        val localRisk = LocalDependencyRisk(dto)

        assertThat(localRisk.canChangeStatus()).isFalse()
    }

} 
