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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
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
import org.sonarlint.intellij.ui.page.EditorTrafficLightPage
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
        beforeMainToolWindowCreated()
        mainToolWindow = SonarLintMainToolWindowPage.create(project)
    }

    /** Tool window tabs resolve their services when created; replace services here so the tabs pick them up. */
    protected open fun beforeMainToolWindowCreated() {
        // no services replaced by default
    }

    @AfterEach
    fun closeMainToolWindow() {
        uiContext.closeOpenEditorsAndSonarLintToolWindows()
    }

    /** Registers a test-data file with SLOOP and opens it in the code-insight test editor. */
    protected fun openFileFromTestData(relativePath: String): VirtualFile {
        val sourcePath = java.nio.file.Path.of(testDataPath, relativePath).toAbsolutePath()
        check(java.nio.file.Files.isRegularFile(sourcePath)) { "Test file not found: $sourcePath" }
        val virtualFile = myFixture.copyFileToProject(relativePath, relativePath)
        myFixture.configureFromExistingVirtualFile(virtualFile)
        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile))),
            true,
        )
        return virtualFile
    }

    /** Selected text editor for [file] (code-insight fixture editor in light tests). */
    protected fun selectedEditorFor(file: VirtualFile): Editor = readOnEdt {
        val fileEditorManager = FileEditorManager.getInstance(project)
        (fileEditorManager.getSelectedEditor(file) as? TextEditor)?.editor
            ?: fileEditorManager.selectedTextEditor
            ?: myFixture.editor
    }

    /** Builds [EditorTrafficLightPage] through the [com.intellij.openapi.editor.markup.InspectionWidgetActionProvider] EP. */
    protected fun trafficLightPageFor(file: VirtualFile): EditorTrafficLightPage {
        val editor = selectedEditorFor(file)
        return EditorTrafficLightPage.create(project, editor, file)
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
