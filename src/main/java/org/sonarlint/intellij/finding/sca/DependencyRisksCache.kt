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

import com.intellij.openapi.components.Service
import java.util.UUID
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

@Service(Service.Level.PROJECT)
class DependencyRisksCache() {

    var dependencyRisks: List<LocalDependencyRisk> = emptyList()

    fun update(dependencyRiskIdsToRemove: Set<UUID>, dependencyRisksToAdd: List<LocalDependencyRisk>, dependencyRisksToUpdate: List<LocalDependencyRisk>) {
        val dependencyRisksToAddFiltered = dependencyRisksToAdd.filter { it.status != DependencyRiskDto.Status.FIXED }
        val dependencyRisksToUpdateFiltered = dependencyRisksToUpdate.filter { it.status != DependencyRiskDto.Status.FIXED }

        val currentDependencyRisks = dependencyRisks.toMutableList()
        currentDependencyRisks.removeAll { it.getId() in dependencyRiskIdsToRemove }
        currentDependencyRisks.addAll(dependencyRisksToAddFiltered)
        val updatedDependencyRiskKeys = dependencyRisksToUpdateFiltered.map { it.getId() }
        currentDependencyRisks.removeAll { it.getId() in updatedDependencyRiskKeys }
        currentDependencyRisks.addAll(dependencyRisksToUpdateFiltered)
        dependencyRisks = currentDependencyRisks
    }

}
