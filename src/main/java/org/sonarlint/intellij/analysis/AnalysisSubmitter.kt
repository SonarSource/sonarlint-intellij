/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import org.apache.commons.lang3.tuple.Pair
import org.jetbrains.annotations.VisibleForTesting
import org.sonarlint.intellij.callable.CheckInCallable
import org.sonarlint.intellij.callable.ShowFindingCallable
import org.sonarlint.intellij.callable.ShowReportCallable
import org.sonarlint.intellij.callable.ShowUpdatedCurrentFileCallable
import org.sonarlint.intellij.callable.UpdateOnTheFlyFindingsCallable
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator
import org.sonarlint.intellij.common.analysis.ForcedLanguage
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.promotion.PromotionProvider
import org.sonarlint.intellij.tasks.GlobalTaskProgressReporter
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ForceAnalyzeResponse

@Service(Service.Level.PROJECT)
class AnalysisSubmitter(private val project: Project, private val coroutineScope: CoroutineScope) {

    val onTheFlyFindingsHolder = OnTheFlyFindingsHolder(project)

    fun analyzeAllFiles() {
        coroutineScope.launch {
            val modules = readAction { ModuleManager.getInstance(project).modules }
            val callback = readActionBlocking { ShowReportCallable(project, modules.size) }
            val tracker = readAction { getService(project, RunningAnalysesTracker::class.java) }
            val backend = getService(BackendService::class.java)
            val taskState = readActionBlocking { createGlobalTaskIfNeeded("Analyzing all projects files", modules.size, true) }
            supervisorScope {
                modules.forEach { module ->
                    launch {
                        analyzeModule(module, backend, tracker, callback, taskState)
                    }
                }
            }
        }
    }

    private suspend fun analyzeModule(
        module: Module,
        backend: BackendService,
        tracker: RunningAnalysesTracker,
        callback: AnalysisCallback,
        taskState: GlobalTaskProgressReporter?
    ) = coroutineScope {
        val cancellation = BackendAnalysisCancellation(backend)
        val owner = currentCoroutineContext().job
        val finished = cancellation.finished
        val taskCompletion = getService(GlobalBackgroundTaskTracker::class.java).onTaskFinished(cancellation::taskFinished)
        try {
            // Submission has side effects and must not be retried when a write action arrives.
            val response = readActionBlocking {
                backend.analyzeFullProject(module).also { future ->
                    // Observe before leaving the read action, whose return can be cancelled.
                    cancellation.observe(future, owner)
                }
            }
            val analysisId = awaitAnalysisId(response)
            val ownedCallback = completionCallback(callback, finished)
            readActionBlocking {
                ensureActive()
                analysisId?.let { tracker.track(AnalysisState(it, ownedCallback, module)) }
                taskState?.trackTask(module, analysisId?.toString())
                // Reconcile progress that ended before its ID was registered.
                if (finished.isCompleted) analysisId?.let { taskState?.taskFinished(it.toString()) }
            }
            if (analysisId != null) finished.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            readActionBlocking {
                callback.onError(e)
                taskState?.trackTask(module, null)
            }
        } finally {
            taskCompletion.close()
            if (owner.isCancelled && !finished.isCompleted) cancellation.cancel()
        }
    }

    private fun completionCallback(callback: AnalysisCallback, finished: CompletableDeferred<Unit>) = object : AnalysisCallback {
        override fun onSuccess(analysisResult: AnalysisResult) {
            if (coroutineScope.isActive) callback.onSuccess(analysisResult)
            finished.complete(Unit)
        }

        override fun onError(e: Throwable) {
            if (coroutineScope.isActive) callback.onError(e)
            finished.complete(Unit)
        }
    }

    private class BackendAnalysisCancellation(private val backend: BackendService) {
        val finished = CompletableDeferred<Unit>()
        private var backendId: UUID? = null
        private var cancellationSent = false
        private val completedBeforeResponse = mutableSetOf<String>()

        fun observe(response: CompletableFuture<ForceAnalyzeResponse>, owner: Job) {
            response.thenAccept { result ->
                setBackendId(result.analysisId)
                if (owner.isCancelled) cancel()
            }
        }

        @Synchronized
        private fun setBackendId(id: UUID?) {
            backendId = id
            if (id?.toString() in completedBeforeResponse) finished.complete(Unit)
            completedBeforeResponse.clear()
        }

        @Synchronized
        fun taskFinished(taskId: String) {
            if (backendId == null) completedBeforeResponse.add(taskId)
            if (backendId?.toString() == taskId) finished.complete(Unit)
        }

        @Synchronized
        fun cancel() {
            backendId?.let {
                if (!cancellationSent && !finished.isCompleted) {
                    cancellationSent = true
                    backend.cancelTask(it.toString())
                }
            }
        }
    }

    private suspend fun awaitAnalysisId(response: CompletableFuture<ForceAnalyzeResponse>): UUID? =
        suspendCancellableCoroutine { continuation ->
            // Keep the response observable: cancelling the future would lose a late backend task ID.
            response.whenComplete { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error)
                } else {
                    continuation.resume(result.analysisId)
                }
            }
        }

    fun analyzeVcsChangedFiles() {
        runOnPooledThread(project) {
            val modules = ModuleManager.getInstance(project).modules
            val callback = ShowReportCallable(project, modules.size)
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
        if (!Settings.getGlobalSettings().isAutoTrigger) return
        runOnPooledThread(project) {
            val callback = UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder)
            val modules = ModuleManager.getInstance(project).modules
            modules.forEach { module ->
                getService(BackendService::class.java).analyzeOpenFiles(module).thenAccept { response ->
                    response.analysisId?.let { analysisId ->
                        val analysisState = AnalysisState(analysisId, callback, module)
                        getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                    }
                }
            }
        }
    }

    fun analyzeFilesPreCommit(files: Set<VirtualFile>): Pair<CheckInCallable, List<UUID>>? {
        val callback = CheckInCallable()
        val analysisIds = mutableListOf<UUID>()
        files.groupBy { file -> findModuleForFile(file, project) }
            .forEach { (module, moduleFiles) -> module?.let { awaitPreCommitAnalysisStart(it, moduleFiles, callback, analysisIds) } }
        return preCommitAnalysisResult(callback, analysisIds)
    }

    private fun awaitPreCommitAnalysisStart(module: Module, files: List<VirtualFile>, callback: CheckInCallable, analysisIds: MutableList<UUID>) {
        getService(project, PromotionProvider::class.java).handlePromotionOnPreCommitCheck()
        val console = SonarLintConsole.get(project)
        val startTime = System.currentTimeMillis()
        console.debug("Pre-commit: submitting analyzeFileList for module '${module.name}' with ${files.size} file(s)")
        val future = getService(BackendService::class.java).analyzeFileList(module, files)
        try {
            future[5, TimeUnit.SECONDS].analysisId?.let { analysisId ->
                console.debug("Pre-commit: analyzeFileList returned analysisId=$analysisId in ${System.currentTimeMillis() - startTime} ms")
                getService(project, RunningAnalysesTracker::class.java).track(AnalysisState(analysisId, callback, module))
                analysisIds.add(analysisId)
            } ?: console.debug("Pre-commit: analyzeFileList returned no analysisId in ${System.currentTimeMillis() - startTime} ms")
        } catch (e: TimeoutException) {
            future.cancel(true)
            reportPreCommitAnalysisStartFailure(callback, e, "Pre-commit analysis timed out while waiting for the analysis to start (after ${System.currentTimeMillis() - startTime} ms)")
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            reportPreCommitAnalysisStartFailure(callback, e, "Pre-commit analysis interrupted")
        } catch (e: ExecutionException) {
            reportPreCommitAnalysisStartFailure(callback, e.cause ?: e, "Pre-commit analysis failed to start")
        } catch (e: Exception) {
            reportPreCommitAnalysisStartFailure(callback, e, "Pre-commit analysis failed to start")
        }
    }

    private fun reportPreCommitAnalysisStartFailure(callback: CheckInCallable, error: Throwable, message: String) {
        SonarLintConsole.get(project).error(message, error)
        callback.onError(error)
    }

    private fun preCommitAnalysisResult(callback: CheckInCallable, analysisIds: List<UUID>): Pair<CheckInCallable, List<UUID>>? = when {
        analysisIds.isNotEmpty() || !callback.analysisSucceeded() -> Pair.of(callback, analysisIds)
        else -> null
    }

    fun analyzeFilesOnUserAction(files: Set<VirtualFile>, actionEvent: AnActionEvent) {
        runOnPooledThread(project) {
            val filesByModule = files.groupBy { file ->
                findModuleForFile(file, project)
            }
            val moduleCount = filesByModule.keys.filterNotNull().size
            
            val callback = if (TOOL_WINDOW_ID == actionEvent.place) {
                ShowUpdatedCurrentFileCallable(project, onTheFlyFindingsHolder)
            } else {
                ShowReportCallable(project, moduleCount)
            }
            
            analyzeFiles(filesByModule, callback)
        }
    }

    /**
     * Only for testing
     */
    @VisibleForTesting
    fun analyzeFilesWithCallback(files: Set<VirtualFile>, callback: AnalysisCallback) {
        if (!ApplicationManager.getApplication().isUnitTestMode) return
        runOnPooledThread(project) {
            val filesByModule = files.groupBy { file ->
                findModuleForFile(file, project)
            }
            analyzeFiles(filesByModule, callback)
        }
    }

    private fun analyzeFiles(filesByModule: Map<Module?, List<VirtualFile>>, callback: AnalysisCallback) {
        val taskState = createGlobalTaskIfNeeded("Analyzing files", filesByModule.size, true)
        filesByModule.forEach { (module, files) ->
            module?.let {
                getService(project, PromotionProvider::class.java).handlePromotionOnAnalysisReport(files)
                getService(BackendService::class.java).analyzeFileList(module, files)
                    .thenAccept { response ->
                        response.analysisId?.let { analysisId ->
                            getService(project, AnalysisStatus::class.java).tryRun(analysisId)
                            val analysisState = AnalysisState(analysisId, callback, module)
                            getService(project, RunningAnalysesTracker::class.java).track(analysisState)
                        }
                        taskState?.trackTask(module, response.analysisId?.toString())
                    }
                    .exceptionally { throwable ->
                        callback.onError(throwable)
                        null
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
            task.queue()
            getService(GlobalBackgroundTaskTracker::class.java).track(task)
            task
        } else null
    }

    companion object {
        fun collectContributedLanguages(module: Module, listFiles: List<VirtualFile>): Map<VirtualFile, ForcedLanguage> {
            val contributedConfigurations = AnalysisConfigurator.EP_NAME.extensionList.stream()
                .map { config -> config.configure(module, listFiles) }

            val contributedLanguages = HashMap<VirtualFile, ForcedLanguage>()
            for (config in contributedConfigurations) {
                contributedLanguages.putAll(config.forcedLanguages)
            }
            return contributedLanguages
        }
    }
}
