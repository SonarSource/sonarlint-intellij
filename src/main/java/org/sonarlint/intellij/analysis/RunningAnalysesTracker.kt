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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.components.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.sonarlint.intellij.common.ui.SonarLintConsole

@Service(Service.Level.PROJECT)
class RunningAnalysesTracker {

    private val analysisStateById: MutableMap<UUID, AnalysisState> = ConcurrentHashMap<UUID, AnalysisState>()

    fun track(analysisState: AnalysisState) {
        analysisStateById[analysisState.id] = analysisState
    }

    fun finish(analysisState: AnalysisState) {
        analysisStateById.remove(analysisState.id)
    }

    fun finishAll() {
        analysisStateById.clear()
    }

    fun getById(analysisId: UUID): AnalysisState? {
        return analysisStateById[analysisId]
    }

    fun cancel(analysisId: UUID) {
        analysisStateById[analysisId]?.let {
            it.cancel()
            finish(it)
        }
    }

    fun cancelSimilarAnalysis(analysisState: AnalysisState, console: SonarLintConsole) {
        for (analysis in analysisStateById.values) {
            if (analysis.isRedundant(analysisState)) {
                console.info("Cancelling analysis ${analysis.id}")
                analysis.cancel()
            }
        }
    }

    fun isAnalysisRunning(): Boolean {
        return analysisStateById.isNotEmpty()
    }

}
