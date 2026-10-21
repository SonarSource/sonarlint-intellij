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
package org.sonarlint.intellij.ui.standalone

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.nio.file.Files
import java.nio.file.Path
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

/**
 * Light UI slot for former Standalone IT `should_copy_fix_prompt_from_current_file_tab` ([SLI-2813](https://github.com/SonarSource/sonarlint-intellij/pull/1844)).
 * Runs automatically once [GenerateFixPromptAction] is on the classpath (when #1844 is stacked on this branch).
 */
class CopyFixPromptLightTests : AbstractSonarLintUILightTests() {

    @BeforeEach
    fun waitForReady() {
        waitUntilAnalysisReady()
    }

    @Test
    @EnabledIf("org.sonarlint.intellij.ui.standalone.CopyFixPromptLightTests#isGenerateFixPromptActionAvailable")
    fun should_copy_fix_prompt_after_findings_analysis() {
        val file = openFooJavaFromStandaloneFixture()
        mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click(file)

        awaitUntilAsserted {
            assertThat(mainToolWindow.findings.issues).isNotEmpty()
        }

        clearNotifications()

        readOnEdt {
            val actionClass = Class.forName("org.sonarlint.intellij.actions.GenerateFixPromptAction")
            @Suppress("UNCHECKED_CAST")
            val action = actionClass.getDeclaredConstructor().newInstance() as AnAction
            val event = mock<AnActionEvent>()
            whenever(event.project).thenReturn(project)
            action.actionPerformed(event)
        }

        awaitUntilAsserted {
            assertThat(getProjectNotifications().map { it.content })
                .anyMatch { it.contains("Fix prompt copied to clipboard") }
        }
    }

    /** Reuses [`StandaloneIdeaLightTests`] Java fixture (empty `Foo` class). */
    private fun openFooJavaFromStandaloneFixture(): com.intellij.openapi.vfs.VirtualFile {
        val standaloneFoo = Path.of("src/test/testData/StandaloneIdeaLightTests/src/Foo.java").toAbsolutePath()
        check(Files.isRegularFile(standaloneFoo)) { "Missing fixture: $standaloneFoo" }
        val content = Files.readString(standaloneFoo)
        val psiFile = myFixture.configureByText("Foo.java", content)
        val virtualFile = psiFile.virtualFile
        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile))),
            true,
        )
        return virtualFile
    }

    companion object {
        @JvmStatic
        fun isGenerateFixPromptActionAvailable(): Boolean =
            runCatching { Class.forName("org.sonarlint.intellij.actions.GenerateFixPromptAction") }.isSuccess
    }
}
