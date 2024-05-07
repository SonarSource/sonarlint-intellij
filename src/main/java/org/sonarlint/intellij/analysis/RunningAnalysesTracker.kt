package org.sonarlint.intellij.analysis

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class RunningAnalysesTracker(private val project: Project) {

    private val analysisStateById: MutableMap<UUID, AnalysisState> = ConcurrentHashMap<UUID, AnalysisState>()

    fun track(analysisState: AnalysisState) {
        analysisStateById[analysisState.id] = analysisState
    }

    fun finish(analysisState: AnalysisState) {
        analysisStateById.remove(analysisState.id)
    }

    fun getById(analysisId: UUID): AnalysisState? {
        return analysisStateById[analysisId]
    }

    fun getByIds(analysisIds: Set<UUID>): Set<AnalysisState> {
        return analysisStateById.filter { analysisIds.contains(it.key) }.map { it.value }.toSet()
    }

}