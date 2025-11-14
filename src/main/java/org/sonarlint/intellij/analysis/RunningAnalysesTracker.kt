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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService

@Service(Service.Level.PROJECT)
class RunningAnalysesTracker(private val project: Project) {

    private val analysisStateById: MutableMap<UUID, AnalysisState> = ConcurrentHashMap<UUID, AnalysisState>()

    fun track(analysisState: AnalysisState) {
        analysisState.id.let { analysisStateById[it] = analysisState }
    }

    fun finish(analysisState: AnalysisState) {
        getService(project, AnalysisStatus::class.java).stopRun(analysisState.id)
        analysisStateById.remove(analysisState.id)
    }

    fun getById(analysisId: UUID): AnalysisState? {
        return analysisStateById[analysisId]
    }

    fun isEmpty(): Boolean {
        return analysisStateById.isEmpty()
    }

    // Used for tests
    fun cancelAll() {
        analysisStateById.keys.forEach { uuid ->
            getService(BackendService::class.java).cancelTask(uuid.toString())
        }
        analysisStateById.clear()
    }

}
