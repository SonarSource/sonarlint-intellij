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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

class DependencyRisksCacheTests {

    private lateinit var cache: DependencyRisksCache

    @BeforeEach
    fun setUp() {
        cache = DependencyRisksCache()
    }

    @Test
    fun should_start_with_empty_dependency_risks() {
        assertThat(cache.dependencyRisks).isEmpty()
    }

    @Test
    fun should_add_new_dependency_risks_on_update() {
        val risk1 = aDependencyRisk(DependencyRiskDto.Status.SAFE)
        val risk2 = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val risksToAdd = listOf(risk1, risk2)

        cache.update(emptySet(), risksToAdd, emptyList())

        assertThat(cache.dependencyRisks).hasSize(2)
        assertThat(cache.dependencyRisks.map { it.getId() }).containsExactly(risk1.getId(), risk2.getId())
    }

    @Test
    fun should_add_dependency_risks_to_existing_ones() {
        val existingRisk = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        cache.dependencyRisks = listOf(existingRisk)

        val newRisk = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        cache.update(emptySet(), listOf(newRisk), emptyList())

        assertThat(cache.dependencyRisks).hasSize(2)
        assertThat(cache.dependencyRisks.map { it.getId() }).containsExactlyInAnyOrder(existingRisk.getId(), newRisk.getId())
    }

    @Test
    fun should_handle_empty_update_parameters() {
        val existingRisk = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        cache.dependencyRisks = listOf(existingRisk)

        cache.update(emptySet(), emptyList(), emptyList())

        assertThat(cache.dependencyRisks).hasSize(1)
        assertThat(cache.dependencyRisks.first().getId()).isEqualTo(existingRisk.getId())
    }

    @Test
    fun should_remove_dependency_risks_by_ids_during_update() {
        val risk1 = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val risk2 = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val risk3 = aDependencyRisk(DependencyRiskDto.Status.SAFE)
        cache.dependencyRisks = listOf(risk1, risk2, risk3)

        cache.update(setOf(risk1.getId(), risk3.getId()), emptyList(), emptyList())

        assertThat(cache.dependencyRisks).hasSize(1)
        assertThat(cache.dependencyRisks.first().getId()).isEqualTo(risk2.getId())
    }

    @Test
    fun should_update_existing_dependency_risks_during_update() {
        val existingRisk = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        cache.dependencyRisks = listOf(existingRisk)

        val updatedRisk = aDependencyRisk(existingRisk.getId(), DependencyRiskDto.Status.SAFE, DependencyRiskDto.Severity.HIGH, emptyList())
        cache.update(emptySet(), emptyList(), listOf(updatedRisk))

        assertThat(cache.dependencyRisks).hasSize(1)
        assertThat(cache.dependencyRisks.first().getId()).isEqualTo(existingRisk.getId())
        assertThat(cache.dependencyRisks.first().isResolved()).isTrue()
    }

    @Test
    fun should_handle_complex_update_with_remove_add_and_update() {
        val existingRisk1 = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val existingRisk2 = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val existingRisk3 = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        cache.dependencyRisks = listOf(existingRisk1, existingRisk2, existingRisk3)

        val newRisk = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val updatedRisk2 = aDependencyRisk(existingRisk2.getId(), DependencyRiskDto.Status.SAFE, DependencyRiskDto.Severity.HIGH, emptyList())

        cache.update(setOf(existingRisk1.getId()), listOf(newRisk), listOf(updatedRisk2))

        assertThat(cache.dependencyRisks).hasSize(3)
        val riskIds = cache.dependencyRisks.map { it.getId() }
        assertThat(riskIds).containsExactlyInAnyOrder(existingRisk3.getId(), newRisk.getId(), existingRisk2.getId())
        
        // Verify the updated risk has the new status
        val updatedRiskInCache = cache.dependencyRisks.find { it.getId() == existingRisk2.getId() }
        assertThat(updatedRiskInCache?.isResolved()).isTrue()
    }

} 
