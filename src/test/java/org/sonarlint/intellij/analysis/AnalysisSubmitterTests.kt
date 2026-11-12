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

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ForceAnalyzeResponse

class AnalysisSubmitterTests : AbstractSonarLintLightTests() {
    private lateinit var mockBackendService: BackendService
    private lateinit var submitter: AnalysisSubmitter
    private lateinit var scope: CoroutineScope
    private val failures = ConcurrentLinkedQueue<Throwable>()
    private var additionalModule: Module? = null

    @BeforeEach
    fun initialization() {
        mockBackendService = mock(BackendService::class.java)
        replaceApplicationService(BackendService::class.java, mockBackendService)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> failures.add(error) })
        submitter = AnalysisSubmitter(project, scope)
    }

    @AfterEach
    fun cleanup() {
        scope.cancel()
        awaitOnEdt { assertThat(scope.coroutineContext[Job]!!.children.toList()).isEmpty() }
        additionalModule?.let { added -> WriteAction.runAndWait<RuntimeException> { ModuleManager.getInstance(project).disposeModule(added) } }
    }

    private fun awaitOnEdt(assertion: () -> Unit) {
        await().pollInSameThread().atMost(5, TimeUnit.SECONDS).untilAsserted {
            // Platform coroutine children can include invokeLater callbacks.
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertion()
        }
    }

    private fun addModule() {
        WriteAction.runAndWait<RuntimeException> {
            additionalModule = ModuleManager.getInstance(project).newModule("${myFixture.tempDirFixture.tempDirPath}/additional.iml", "JAVA_MODULE")
        }
    }

    @Test
    fun `failed submissions complete global progress for all modules`() {
        addModule()
        val tasks = spy(GlobalBackgroundTaskTracker())
        replaceApplicationService(GlobalBackgroundTaskTracker::class.java, tasks)
        doReturn(CompletableFuture.failedFuture<ForceAnalyzeResponse>(IllegalStateException("Analysis could not start")))
            .whenever(mockBackendService).analyzeFullProject(any())

        submitter.analyzeAllFiles()

        awaitOnEdt {
            verify(tasks).untrackGlobalTask(any())
            assertThat(scope.coroutineContext[Job]!!.children.toList()).isEmpty()
        }
        assertThat(failures).isEmpty()
    }

    @Test
    fun `tasks completed before responses complete global progress for all modules`() {
        addModule()
        val tasks = spy(GlobalBackgroundTaskTracker())
        replaceApplicationService(GlobalBackgroundTaskTracker::class.java, tasks)
        whenever(mockBackendService.analyzeFullProject(any())).thenAnswer {
            val id = UUID.randomUUID()
            tasks.finishTask(id.toString())
            CompletableFuture.completedFuture(ForceAnalyzeResponse(id))
        }

        submitter.analyzeAllFiles()

        awaitOnEdt {
            verify(tasks).untrackGlobalTask(any())
            assertThat(scope.coroutineContext[Job]!!.children.toList()).isEmpty()
        }
        scope.cancel()
        verify(mockBackendService, never()).cancelTask(any())
        assertThat(failures).isEmpty()
    }

    @Test
    fun `full project cancellation before response cancels the late backend task`() {
        val response = CompletableFuture<ForceAnalyzeResponse>()
        doReturn(response).whenever(mockBackendService).analyzeFullProject(module)
        submitter.analyzeAllFiles()
        awaitOnEdt { verify(mockBackendService).analyzeFullProject(module) }

        scope.cancel()
        val id = UUID.randomUUID()
        response.complete(ForceAnalyzeResponse(id))

        awaitOnEdt { verify(mockBackendService).cancelTask(id.toString()) }
        assertThat(response.isCancelled).isFalse
        assertThat(getService(project, RunningAnalysesTracker::class.java).getById(id)).isNull()
    }

    @Test
    fun `cancellation during submission still observes the backend response`() {
        val response = CompletableFuture<ForceAnalyzeResponse>()
        whenever(mockBackendService.analyzeFullProject(module)).thenAnswer {
            scope.cancel()
            response
        }
        submitter.analyzeAllFiles()
        awaitOnEdt { verify(mockBackendService).analyzeFullProject(module) }

        val id = UUID.randomUUID()
        response.complete(ForceAnalyzeResponse(id))

        awaitOnEdt { verify(mockBackendService).cancelTask(id.toString()) }
        assertThat(getService(project, RunningAnalysesTracker::class.java).getById(id)).isNull()
    }

    @Test
    fun `cancelled scope does not submit full project analysis`() {
        scope.cancel()

        submitter.analyzeAllFiles()

        verify(mockBackendService, never()).analyzeFullProject(any())
    }

    @Test
    fun `response without an analysis id completes the operation`() {
        doReturn(CompletableFuture.completedFuture(ForceAnalyzeResponse(null))).whenever(mockBackendService).analyzeFullProject(module)

        submitter.analyzeAllFiles()

        awaitOnEdt {
            verify(mockBackendService).analyzeFullProject(module)
            assertThat(scope.coroutineContext[Job]!!.children.toList()).isEmpty()
        }
        assertThat(failures).isEmpty()
        verify(mockBackendService, never()).cancelTask(any())
    }

    @Test
    fun `failed submission completes the operation`() {
        val failure = IllegalStateException("Analysis could not start")
        doReturn(CompletableFuture.failedFuture<ForceAnalyzeResponse>(failure)).whenever(mockBackendService).analyzeFullProject(module)

        submitter.analyzeAllFiles()

        awaitOnEdt {
            verify(mockBackendService).analyzeFullProject(module)
            assertThat(scope.coroutineContext[Job]!!.children.toList()).isEmpty()
        }
        assertThat(failures).isEmpty()
        verify(mockBackendService, never()).cancelTask(any())
    }

    @Test
    fun `backend task completion releases ownership without final findings`() {
        val id = UUID.randomUUID()
        doReturn(CompletableFuture.completedFuture(ForceAnalyzeResponse(id))).whenever(mockBackendService).analyzeFullProject(module)
        val tracker = getService(project, RunningAnalysesTracker::class.java)
        submitter.analyzeAllFiles()
        awaitOnEdt { assertThat(tracker.getById(id)).isNotNull() }

        getService(GlobalBackgroundTaskTracker::class.java).finishTask(id.toString())

        awaitOnEdt { assertThat(scope.coroutineContext[Job]!!.children.toList()).isEmpty() }
        // Findings can arrive after the progress end notification.
        tracker.getById(id)!!.addRawIssues(id, emptyMap(), false)
        assertThat(tracker.getById(id)).isNull()
        scope.cancel()
        verify(mockBackendService, never()).cancelTask(id.toString())
    }

    @Test
    fun `backend task completion before submission response releases ownership`() {
        val id = UUID.randomUUID()
        val response = CompletableFuture<ForceAnalyzeResponse>()
        doReturn(response).whenever(mockBackendService).analyzeFullProject(module)
        submitter.analyzeAllFiles()
        awaitOnEdt { verify(mockBackendService).analyzeFullProject(module) }

        getService(GlobalBackgroundTaskTracker::class.java).finishTask(id.toString())
        response.complete(ForceAnalyzeResponse(id))

        awaitOnEdt { assertThat(scope.coroutineContext[Job]!!.children.toList()).isEmpty() }
        scope.cancel()
        verify(mockBackendService, never()).cancelTask(id.toString())
    }

    @Test
    fun `full project cancellation after response cancels the running backend task`() {
        val id = UUID.randomUUID()
        doReturn(CompletableFuture.completedFuture(ForceAnalyzeResponse(id))).whenever(mockBackendService).analyzeFullProject(module)
        val tracker = getService(project, RunningAnalysesTracker::class.java)
        submitter.analyzeAllFiles()
        awaitOnEdt { assertThat(tracker.getById(id)).isNotNull() }

        scope.cancel()

        awaitOnEdt { verify(mockBackendService).cancelTask(id.toString()) }
    }

    @Test
    fun `completed full project analysis is not cancelled with the scope`() {
        val id = UUID.randomUUID()
        doReturn(CompletableFuture.completedFuture(ForceAnalyzeResponse(id))).whenever(mockBackendService).analyzeFullProject(module)
        val tracker = getService(project, RunningAnalysesTracker::class.java)
        submitter.analyzeAllFiles()
        awaitOnEdt { assertThat(tracker.getById(id)).isNotNull() }
        tracker.getById(id)!!.addRawIssues(id, emptyMap(), false)
        awaitOnEdt { assertThat(tracker.getById(id)).isNull() }

        scope.cancel()

        verify(mockBackendService, never()).cancelTask(id.toString())
    }

    @Test
    fun `analyzeAllFiles should call analyzeFullProject and track`() {
        val future = CompletableFuture.completedFuture(mock(ForceAnalyzeResponse::class.java))
        doReturn(future).whenever(mockBackendService).analyzeFullProject(module)

        submitter.analyzeAllFiles()

        awaitOnEdt { verify(mockBackendService).analyzeFullProject(module) }
    }

    @Test
    fun `analyzeVcsChangedFiles should call analyzeVCSChangedFiles and track`() {
        val future = CompletableFuture.completedFuture(mock(ForceAnalyzeResponse::class.java))
        doReturn(future).whenever(mockBackendService).analyzeVCSChangedFiles(module)

        submitter.analyzeVcsChangedFiles()

        awaitOnEdt { verify(mockBackendService).analyzeVCSChangedFiles(module) }
    }

    @Test
    fun `autoAnalyzeOpenFiles should call analyzeOpenFiles and track`() {
        val future = CompletableFuture.completedFuture(mock(ForceAnalyzeResponse::class.java))
        doReturn(future).whenever(mockBackendService).analyzeOpenFiles(module)

        submitter.autoAnalyzeOpenFiles()

        awaitOnEdt { verify(mockBackendService).analyzeOpenFiles(module) }
    }

    @Test
    fun `analyzeFilesPreCommit should not throw when backend future completes exceptionally`() {
        val psiFile = myFixture.configureByText("Foo.java", "class Foo {}")
        val vFile = psiFile.virtualFile
        val failedFuture = CompletableFuture.failedFuture<ForceAnalyzeResponse>(RuntimeException("test failure"))
        doReturn(failedFuture).whenever(mockBackendService).analyzeFileList(eq(module), any())

        val result = submitter.analyzeFilesPreCommit(setOf(vFile))

        assertThat(result).isNotNull
        assertThat(result!!.left.analysisSucceeded()).isFalse
        assertThat(result.right).isEmpty()
    }
} 
