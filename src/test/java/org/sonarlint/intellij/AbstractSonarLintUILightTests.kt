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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
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
import org.sonarlint.intellij.actions.SonarAnalyzeFilesAction
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarlint.intellij.ui.page.SonarLintMainToolWindowPage
import org.sonarlint.intellij.ui.page.SonarLintUiTestContext
import org.sonarlint.intellij.ui.page.settings.global.SonarLintGlobalSettingsPage
import org.sonarlint.intellij.ui.page.settings.project.SonarLintProjectSettingsPage
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
        openSettingsPages.forEach { it.close() }
        openSettingsPages.clear()
    }

    private val openSettingsPages = mutableListOf<AutoCloseable>()

    /**
     * Opens **Settings → Tools → SonarQube for IDE** and returns a page object for that configurable.
     * Call [SonarLintGlobalSettingsPage.save] to persist changes (equivalent to OK/Apply on the settings dialog).
     */
    protected fun openSqIdeSettings(): SonarLintGlobalSettingsPage =
        SonarLintGlobalSettingsPage.open(project).also { openSettingsPages.add(it) }

    /**
     * Opens **SonarQube for IDE → Project Settings** (same entry as the Findings toolbar configure action).
     */
    protected fun openSqIdeProjectSettings(): SonarLintProjectSettingsPage =
        SonarLintProjectSettingsPage.open(project).also { openSettingsPages.add(it) }

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

    /** Copies a test-data file into the module and notifies SLOOP (does not open the editor). */
    protected fun copyFileFromTestData(relativePath: String): VirtualFile {
        val sourcePath = Path.of(testDataPath, relativePath).toAbsolutePath()
        check(Files.isRegularFile(sourcePath)) { "Test file not found: $sourcePath" }
        val virtualFile = myFixture.copyFileToProject(relativePath, relativePath)
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

    /** Runs **Analyze with SonarQube for IDE** on [files] and opens a **Report** tab (editor context menu place). */
    protected fun analyzeFilesForReport(vararg files: VirtualFile) {
        readOnEdt {
            val action = SonarAnalyzeFilesAction()
            val presentation = Presentation()
            val event = AnActionEvent(
                null,
                { dataId ->
                    when {
                        CommonDataKeys.PROJECT.`is`(dataId) -> project
                        CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> files
                        else -> null
                    }
                },
                ActionPlaces.EDITOR_POPUP,
                presentation,
                ActionManager.getInstance(),
                0,
            )
            action.actionPerformed(event)
            pumpEdt()
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
