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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ForceAnalyzeResponse

class AnalysisSubmitterTests : AbstractSonarLintLightTests() {
    private lateinit var actualBackendService: BackendService
    private lateinit var mockBackendService: BackendService
    private lateinit var submitter: AnalysisSubmitter

    @BeforeEach
    fun initialization() {
        actualBackendService = ApplicationManager.getApplication().getService(BackendService::class.java)
        mockBackendService = mock(BackendService::class.java)
        ApplicationManager.getApplication().replaceService(BackendService::class.java, mockBackendService, testRootDisposable)
        submitter = AnalysisSubmitter(project)
    }

    @AfterEach
    fun cleanup() {
        ApplicationManager.getApplication().replaceService(BackendService::class.java, actualBackendService, applicationLevelDisposable)
    }

    @Test
    fun `analyzeAllFiles should call analyzeFullProject and track`() {
        val future = CompletableFuture.completedFuture(mock(ForceAnalyzeResponse::class.java))
        doReturn(future).whenever(mockBackendService).analyzeFullProject(module)

        submitter.analyzeAllFiles()

        await().atMost(1, TimeUnit.SECONDS).untilAsserted { verify(mockBackendService).analyzeFullProject(module) }
    }

    @Test
    fun `analyzeVcsChangedFiles should call analyzeVCSChangedFiles and track`() {
        val future = CompletableFuture.completedFuture(mock(ForceAnalyzeResponse::class.java))
        doReturn(future).whenever(mockBackendService).analyzeVCSChangedFiles(module)

        submitter.analyzeVcsChangedFiles()

        await().atMost(1, TimeUnit.SECONDS).untilAsserted { verify(mockBackendService).analyzeVCSChangedFiles(module) }
    }

    @Test
    fun `autoAnalyzeOpenFiles should call analyzeOpenFiles and track`() {
        val future = CompletableFuture.completedFuture(mock(ForceAnalyzeResponse::class.java))
        doReturn(future).whenever(mockBackendService).analyzeOpenFiles(module)

        submitter.autoAnalyzeOpenFiles()

        await().atMost(1, TimeUnit.SECONDS).untilAsserted { verify(mockBackendService).analyzeOpenFiles(module) }
    }
} 
