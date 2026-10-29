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
package org.sonarlint.intellij

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarlint.intellij.ui.page.SonarLintMainToolWindowPage
import org.sonarlint.intellij.ui.page.SonarLintUiTestContext
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

abstract class AbstractSonarLintUILightTests : AbstractSonarLintLightTests() {

    protected lateinit var mainToolWindow: SonarLintMainToolWindowPage
    private lateinit var uiContext: SonarLintUiTestContext

    @BeforeEach
    fun openMainToolWindow() {
        // Unit test mode skips StartServicesOnProjectOpened; register editor listeners before tests open files.
        getService(project, AnalysisSubmitter::class.java)
        uiContext = SonarLintUiTestContext(project)
        mainToolWindow = SonarLintMainToolWindowPage.create(project)
    }

    @AfterEach
    fun closeMainToolWindow() {
        uiContext.closeOpenEditorsAndSonarLintToolWindows()
    }

    /** Copies a file from the test class `testData` tree into the module and notifies SLOOP. */
    protected fun registerFileFromTestData(relativePath: String): VirtualFile {
        val sourcePath = Path.of(testDataPath, relativePath).toAbsolutePath()
        check(Files.isRegularFile(sourcePath)) { "Test file not found: $sourcePath" }
        val content = Files.readString(sourcePath)
        val virtualFile = createTestPsiFile(relativePath, content).virtualFile
        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile))),
            true,
        )
        return virtualFile
    }

    /** Registers a test-data file with SLOOP and opens it in the editor (MAIN_EDITOR via code insight fixture). */
    protected fun openFileFromTestData(relativePath: String): VirtualFile {
        val sourcePath = Path.of(testDataPath, relativePath).toAbsolutePath()
        check(Files.isRegularFile(sourcePath)) { "Test file not found: $sourcePath" }
        val virtualFile = myFixture.copyFileToProject(relativePath, relativePath)
        myFixture.configureFromExistingVirtualFile(virtualFile)
        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile))),
            true,
        )
        return virtualFile
    }

    protected fun waitUntilAnalysisReady(timeoutSeconds: Long = 30, module: Module = this.module) {
        Awaitility.await().atMost(timeoutSeconds, TimeUnit.SECONDS).untilAsserted {
            val readiness = getService(project, AnalysisReadinessCache::class.java)
            assertThat(readiness.isModuleReady(module)).isTrue()
            assertThat(readiness.isProjectReady).isTrue()
        }
    }

    /** Polls on the EDT until [assertions] pass ([Awaitility.pollInSameThread]). */
    protected fun awaitUntilAsserted(timeoutSeconds: Long = 60, assertions: () -> Unit) {
        Awaitility.await()
            .pollInSameThread()
            .atMost(timeoutSeconds, TimeUnit.SECONDS)
            .untilAsserted {
                readOnEdt { assertions() }
            }
    }
}
