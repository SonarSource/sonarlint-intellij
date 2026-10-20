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

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.sonarlint.intellij.callable.UpdateOnTheFlyFindingsCallable
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

data class AnalyzedFile(val file: VirtualFile, val issueCount: Int)

/**
 * Runs real on-the-fly analysis in light UI tests (registers test files with SLOOP, awaits [AnalysisSubmitter] results).
 * Tool-window wiring comes from SLI-2980 (`SonarLintMainToolWindowPage` / `HeadlessToolWindow.kt`).
 */
class LightTestAnalysis(
    private val project: Project,
    private val module: Module,
    private val testDataPath: String,
    private val createTestPsiFile: (relativePath: String, content: String) -> PsiFile,
) {

    fun registerFileFromTestData(relativePath: String): VirtualFile {
        val sourcePath = Path.of(testDataPath, relativePath).toAbsolutePath()
        check(Files.isRegularFile(sourcePath)) { "Test file not found: $sourcePath" }
        val content = Files.readString(sourcePath)
        val virtualFile = createTestPsiFile(relativePath, content).virtualFile
        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile))),
            true,
        )
        Thread.sleep(3000)
        return virtualFile
    }

    fun runOnTheFlyAnalysis(file: VirtualFile): AnalyzedFile {
        val submitter = getService(project, AnalysisSubmitter::class.java)
        val holderUpdate = UpdateOnTheFlyFindingsCallable(submitter.onTheFlyFindingsHolder)
        val resultRef = AtomicReference<AnalysisResult>()
        val errorRef = AtomicReference<Throwable>()

        submitter.analyzeFilesWithCallback(
            setOf(file),
            object : AnalysisCallback {
                override fun onSuccess(result: AnalysisResult) {
                    holderUpdate.onSuccess(result)
                    resultRef.set(result)
                }

                override fun onError(e: Throwable) {
                    errorRef.set(e)
                }
            },
        )

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until {
            resultRef.get() != null || errorRef.get() != null
        }
        errorRef.get()?.let { throw RuntimeException("Analysis failed", it) }

        val result = resultRef.get()
        val analyzedFile = result.findings.issuesPerFile.keys.single()
        val issues = result.findings.issuesPerFile.getValue(analyzedFile)
        assertThat(issues).isNotEmpty
        return AnalyzedFile(analyzedFile, issues.size)
    }
}
