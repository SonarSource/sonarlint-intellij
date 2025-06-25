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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.apache.commons.lang3.tuple.Pair
import org.sonarlint.intellij.callable.CheckInCallable
import org.sonarlint.intellij.callable.ShowFindingCallable
import org.sonarlint.intellij.callable.ShowReportCallable
import org.sonarlint.intellij.callable.ShowUpdatedCurrentFileCallable
import org.sonarlint.intellij.callable.UpdateOnTheFlyFindingsCallable
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator
import org.sonarlint.intellij.common.analysis.ForcedLanguage
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.promotion.PromotionProvider
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile

@Service(Service.Level.PROJECT)
class AnalysisSubmitter(private val project: Project) {

    val onTheFlyFindingsHolder = OnTheFlyFindingsHolder(project)

    fun analyzeAllFiles() {
        val callback = ShowReportCallable(project)
        ModuleManager.getInstance(project).modules.forEach { module ->
            getService(BackendService::class.java).analyzeFullProject(module).thenAcceptAsync { response ->
                response.analysisId?.let { analysisId ->
                    val analysisState = AnalysisState(analysisId, callback, module)
                    getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                }
            }
        }
    }

    fun analyzeVcsChangedFiles() {
        val callback = ShowReportCallable(project)
        ModuleManager.getInstance(project).modules.forEach { module ->
            getService(BackendService::class.java).analyzeVCSChangedFiles(module).thenAcceptAsync { response ->
                response.analysisId?.let { analysisId ->
                    val analysisState = AnalysisState(analysisId, callback, module)
                    getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                }
            }
        }
    }

    fun autoAnalyzeSelectedFiles() {
        val callback = UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder)
        FileEditorManager.getInstance(project).selectedFiles.groupBy { selectedFile ->
            findModuleForFile(selectedFile, project)
        }.forEach { (module, files) ->
            module?.let {
                getService(project, PromotionProvider::class.java).handlePromotionOnAnalysisReport(files)
                getService(BackendService::class.java).analyzeFileList(module, files).thenAcceptAsync { response ->
                    response.analysisId?.let { analysisId ->
                        val analysisState = AnalysisState(analysisId, callback, module)
                        getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    }
                }
            }
        }
    }

    fun autoAnalyzeFiles(files: List<VirtualFile>) {
        val callback = UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder)
        files.groupBy { selectedFile ->
            findModuleForFile(selectedFile, project)
        }.forEach { (module, files) ->
            module?.let {
                getService(project, PromotionProvider::class.java).handlePromotionOnAnalysis()
                getService(BackendService::class.java).analyzeFileList(module, files).thenAcceptAsync { response ->
                    response.analysisId?.let { analysisId ->
                        val analysisState = AnalysisState(analysisId, callback, module)
                        getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    }
                }
            }
        }
    }

    fun analyzeFilesPreCommit(files: MutableCollection<VirtualFile>): Pair<CheckInCallable, MutableList<UUID>>? {
        val callback = CheckInCallable()
        val analysisIds = mutableListOf<UUID>()
        files.groupBy { file ->
            findModuleForFile(file, project)
        }.forEach { (module, files) ->
            module?.let {
                getService(project, PromotionProvider::class.java).handlePromotionOnPreCommitCheck()
                val response = getService(BackendService::class.java).analyzeFileList(module, files)[5, TimeUnit.SECONDS]
                response.analysisId?.let { analysisId ->
                    val analysisState = AnalysisState(analysisId, callback, module)
                    getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    analysisIds.add(analysisId)
                }
            }
        }
        if (analysisIds.isEmpty()) {
            return null
        }
        return Pair.of<CheckInCallable, MutableList<UUID>>(callback, analysisIds)
    }

    fun analyzeFilesOnUserAction(files: MutableCollection<VirtualFile>, actionEvent: AnActionEvent) {
        val callback = if (SonarLintToolWindowFactory.TOOL_WINDOW_ID == actionEvent.place) {
            ShowUpdatedCurrentFileCallable(project, onTheFlyFindingsHolder)
        } else {
            ShowReportCallable(project)
        }
        files.groupBy { file ->
            findModuleForFile(file, project)
        }.forEach { (module, files) ->
            module?.let {
                getService(project, PromotionProvider::class.java).handlePromotionOnAnalysisReport(files)
                getService(BackendService::class.java).analyzeFileList(module, files).thenAcceptAsync { response ->
                    response.analysisId?.let { analysisId ->
                        getService(project, AnalysisStatus::class.java).tryRun(analysisId)
                        val analysisState = AnalysisState(analysisId, callback, module)
                        getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    }
                }
            }
        }
    }

    fun <T: Finding> analyzeFileAndTrySelectFinding(showFinding: ShowFinding<T>) {
        getService(project, OpenInIdeFindingCache::class.java).analysisQueued = true
        val callback = ShowFindingCallable(project, onTheFlyFindingsHolder, showFinding)
        findModuleForFile(showFinding.file, project)?.let { module ->
            getService(BackendService::class.java).analyzeFileList(module, listOf(showFinding.file)).thenAcceptAsync { response ->
                response.analysisId?.let { analysisId ->
                    getService(project, OpenInIdeFindingCache::class.java).finding = null
                    getService(project, OpenInIdeFindingCache::class.java).analysisQueued = false
                    val analysisState = AnalysisState(analysisId, callback, module)
                    getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                }
            }
        }
    }

    companion object {
        fun collectContributedLanguages(module: Module, listFiles: List<VirtualFile>): MutableMap<VirtualFile, ForcedLanguage> {
            val contributedConfigurations = AnalysisConfigurator.EP_NAME.getExtensionList().stream()
                .map { config -> config.configure(module, listFiles) }

            val contributedLanguages = HashMap<VirtualFile, ForcedLanguage>()
            for (config in contributedConfigurations) {
                contributedLanguages.putAll(config.forcedLanguages)
            }
            return contributedLanguages
        }
    }
}
