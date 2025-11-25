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
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.timeout
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService

class EditorOpenTriggerTests : AbstractSonarLintLightTests() {

    private lateinit var backendService: BackendService
    private lateinit var editorTrigger: EditorOpenTrigger
    private lateinit var editorManager: FileEditorManager

    @BeforeEach
    fun setup() {
        backendService = mock(BackendService::class.java)
        replaceApplicationService(BackendService::class.java, backendService)
        editorTrigger = EditorOpenTrigger(project)
        globalSettings.isAutoTrigger = true
        editorManager = mock(FileEditorManager::class.java)
        `when`(editorManager.project).thenReturn(project)
        clearInvocations(backendService)
    }

    @Test
    fun `should notify backend when file is opened`() {
        val file = createTestFile("MyClass.java", "class MyClass {}")
        
        editorTrigger.fileOpened(editorManager, file)

        verify(backendService, timeout(2000)).didOpenFile(module, file)
    }

    @Test
    fun `should not notify backend when file is closed`() {
        val file = createTestFile("MyClass.java", "class MyClass {}")

        editorTrigger.fileClosed(editorManager, file)

        verify(backendService, Mockito.never()).didCloseFile(any<Module>(), any<VirtualFile>())
    }
}
