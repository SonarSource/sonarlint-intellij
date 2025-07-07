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
import org.sonarlint.intellij.tasks.GlobalTaskProgressReporter
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

@Service(Service.Level.PROJECT)
class AnalysisSubmitter(private val project: Project) {

    val onTheFlyFindingsHolder = OnTheFlyFindingsHolder(project)

    fun analyzeAllFiles() {
        runOnPooledThread(project) {
            val callback = ShowReportCallable(project)
            val modules = ModuleManager.getInstance(project).modules
            val taskState = createGlobalTaskIfNeeded("Analyzing all projects files", modules.size, true)
            modules.forEach { module ->
                getService(BackendService::class.java).analyzeFullProject(module).thenAccept { response ->
                    response.analysisId?.let { analysisId ->
                        val analysisState = AnalysisState(analysisId, callback, module)
                        getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    }
                    taskState?.trackTask(module, response.analysisId?.toString())
                }
            }
        }
    }

    fun analyzeVcsChangedFiles() {
        runOnPooledThread(project) {
            val callback = ShowReportCallable(project)
            val modules = ModuleManager.getInstance(project).modules
            val taskState = createGlobalTaskIfNeeded("Analyzing VCS changed files", modules.size, false)
            modules.forEach { module ->
                getService(BackendService::class.java).analyzeVCSChangedFiles(module).thenAccept { response ->
                    response.analysisId?.let { analysisId ->
                        val analysisState = AnalysisState(analysisId, callback, module)
                        getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    }
                    taskState?.trackTask(module, response.analysisId?.toString())
                }
            }
        }
    }

    fun autoAnalyzeOpenFiles() {
        runOnPooledThread(project) {
            val callback = UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder)
            val modules = ModuleManager.getInstance(project).modules
            val taskState = createGlobalTaskIfNeeded("Analyzing open files", modules.size, false)
            modules.forEach { module ->
                getService(BackendService::class.java).analyzeOpenFiles(module).thenAccept { response ->
                    response.analysisId?.let { analysisId ->
                        val analysisState = AnalysisState(analysisId, callback, module)
                        getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    }
                    taskState?.trackTask(module, response.analysisId?.toString())
                }
            }
        }
    }

    fun autoAnalyzeFiles(files: List<VirtualFile>) {
        runOnPooledThread(project) {
            val callback = UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder)
            val filesByModule = files.groupBy { selectedFile ->
                findModuleForFile(selectedFile, project)
            }
            val taskState = createGlobalTaskIfNeeded("Analyzing files", filesByModule.size, true)
            filesByModule.forEach { (module, files) ->
                module?.let {
                    getService(project, PromotionProvider::class.java).handlePromotionOnAnalysis()
                    getService(BackendService::class.java).analyzeFileList(module, files).thenAccept { response ->
                        response.analysisId?.let { analysisId ->
                            GlobalLogOutput.get().log("Triggered analysis '$analysisId'", ClientLogOutput.Level.DEBUG)
                            val analysisState = AnalysisState(analysisId, callback, module)
                            getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                        }
                        taskState?.trackTask(module, response.analysisId?.toString())
                    }
                }
            }
        }
    }

    fun analyzeFilesPreCommit(files: Set<VirtualFile>): Pair<CheckInCallable, List<UUID>>? {
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
        return Pair.of(callback, analysisIds)
    }

    fun analyzeFilesOnUserAction(files: Set<VirtualFile>, actionEvent: AnActionEvent) {
        runOnPooledThread(project) {
            val callback = if (SonarLintToolWindowFactory.TOOL_WINDOW_ID == actionEvent.place) {
                ShowUpdatedCurrentFileCallable(project, onTheFlyFindingsHolder)
            } else {
                ShowReportCallable(project)
            }
            val filesByModule = files.groupBy { file ->
                findModuleForFile(file, project)
            }
            val taskState = createGlobalTaskIfNeeded("Analyzing files", filesByModule.size, true)
            filesByModule.forEach { (module, files) ->
                module?.let {
                    getService(project, PromotionProvider::class.java).handlePromotionOnAnalysisReport(files)
                    getService(BackendService::class.java).analyzeFileList(module, files).thenAccept { response ->
                        response.analysisId?.let { analysisId ->
                            getService(project, AnalysisStatus::class.java).tryRun(analysisId)
                            val analysisState = AnalysisState(analysisId, callback, module)
                            getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                        }
                        taskState?.trackTask(module, response.analysisId?.toString())
                    }
                }
            }
        }
    }

    fun <T: Finding> analyzeFileAndTrySelectFinding(showFinding: ShowFinding<T>) {
        runOnPooledThread(project) {
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
    }

    private fun createGlobalTaskIfNeeded(title: String, moduleSize: Int, withTextUpdate: Boolean): GlobalTaskProgressReporter? {
        return if (moduleSize > 1) {
            val task = GlobalTaskProgressReporter(project, title, moduleSize, withTextUpdate)
            task.updateText("SonarQube: Analysis 1 out of $moduleSize modules")
            task.queue()
            getService(GlobalBackgroundTaskTracker::class.java).track(task)
            task
        } else null
    }

    companion object {
        fun collectContributedLanguages(module: Module, listFiles: List<VirtualFile>): Map<VirtualFile, ForcedLanguage> {
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
