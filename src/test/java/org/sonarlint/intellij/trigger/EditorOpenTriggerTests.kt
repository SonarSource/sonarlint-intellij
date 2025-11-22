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
package org.sonarlint.intellij.trigger

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService

class EditorOpenTriggerTests : AbstractSonarLintLightTests() {

    private val backendService = mock(BackendService::class.java)
    private lateinit var editorTrigger: EditorOpenTrigger
    private lateinit var file: VirtualFile
    private lateinit var editorManager: FileEditorManager

    @BeforeEach
    fun start() {
        replaceApplicationService(BackendService::class.java, backendService)

        editorTrigger = EditorOpenTrigger(project)
        globalSettings.isAutoTrigger = true

        file = createTestFile("MyClass.java", "class MyClass{}")
        editorManager = mock(FileEditorManager::class.java)
        `when`(editorManager.project).thenReturn(project)

        editorTrigger.onProjectOpened()

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, AnalysisReadinessCache::class.java).isModuleReady(module)).isTrue()
        }

        // Clear interactions from setup
        clearInvocations(backendService)
    }

    @Test
    fun should_trigger_open_file() {
        editorTrigger.fileOpened(editorManager, file)

        verify(backendService).didOpenFile(module, file)
    }

    @Test
    fun should_do_nothing_closed() {
        editorTrigger.fileClosed(editorManager, file)
        editorTrigger.selectionChanged(FileEditorManagerEvent(editorManager, null, null))

        verifyNoInteractions(backendService)
    }

}

