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
package org.sonarlint.intellij.fs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verifyNoInteractions
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

private const val FILE_NAME = "main.py"

class VirtualFileSystemListenerTests : AbstractSonarLintLightTests() {

    @BeforeEach
    fun prepare() {
        replaceProjectService(ProjectBindingManager::class.java, projectBindingManager)
        actualBackendService = ApplicationManager.getApplication().getService(BackendService::class.java)
        backendService = spy(actualBackendService)
        ApplicationManager.getApplication().replaceService(BackendService::class.java, backendService, testRootDisposable)
        file = myFixture.copyFileToProject(FILE_NAME, FILE_NAME)
        virtualFileSystemListener = VirtualFileSystemListener()
    }

    @Test
    fun should_notify_of_a_file_deleted_event() {
        val vFileEvent = VFileDeleteEvent(null, file, false)

        clearInvocations(backendService)
        virtualFileSystemListener.before(listOf(vFileEvent))

        val paramsCaptor = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        val secondParamsCaptor = argumentCaptor<Boolean>()
        verify(backendService, timeout(3000)).updateFileSystem(paramsCaptor.capture(), secondParamsCaptor.capture())

        assertThat(paramsCaptor.allValues).hasSize(1)
        assertThat(paramsCaptor.firstValue.values).hasSize(1)
        paramsCaptor.firstValue.values.forEach {
            assertThat(it[0].type).isEqualTo(ModuleFileEvent.Type.DELETED)
            assertThat(it[0].virtualFile).isEqualTo(file)
        }
        assertThat(secondParamsCaptor.firstValue).isFalse()
    }

    @Test
    fun should_not_notify_of_a_non_py_file() {
        val nonPyFile = myFixture.copyFileToProject("file.txt", "file.txt")
        val vFileEvent = VFileDeleteEvent(null, nonPyFile, false)

        clearInvocations(backendService)
        virtualFileSystemListener.before(listOf(vFileEvent))
    }

    @Test
    fun should_notify_of_a_file_modified_event() {
        val vFileEvent = VFileContentChangeEvent(null, file, 0L, 0L, false)

        clearInvocations(backendService)
        virtualFileSystemListener.after(listOf(vFileEvent))

        val paramsCaptor = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        val secondParamsCaptor = argumentCaptor<Boolean>()
        verify(backendService, timeout(3000)).updateFileSystem(paramsCaptor.capture(), secondParamsCaptor.capture())

        assertThat(paramsCaptor.allValues).hasSize(1)
        assertThat(paramsCaptor.firstValue.values).hasSize(1)
        paramsCaptor.firstValue.values.forEach {
            assertThat(it[0].type).isEqualTo(ModuleFileEvent.Type.MODIFIED)
            assertThat(it[0].virtualFile).isEqualTo(file)
        }
        assertThat(secondParamsCaptor.firstValue).isFalse()
    }

    @Test
    fun should_notify_of_a_file_created_event() {
        val vFileEvent = VFileCreateEvent(null, file.parent, FILE_NAME, false, null, null, false, null)

        clearInvocations(backendService)
        virtualFileSystemListener.after(listOf(vFileEvent))

        val paramsCaptor = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        val secondParamsCaptor = argumentCaptor<Boolean>()
        verify(backendService, timeout(3000)).updateFileSystem(paramsCaptor.capture(), secondParamsCaptor.capture())

        assertThat(paramsCaptor.allValues).hasSize(1)
        assertThat(paramsCaptor.firstValue.values).hasSize(1)
        paramsCaptor.firstValue.values.forEach {
            assertThat(it[0].type).isEqualTo(ModuleFileEvent.Type.CREATED)
            assertThat(it[0].virtualFile).isEqualTo(file)
        }
        assertThat(secondParamsCaptor.firstValue).isFalse()
    }

    @Test
    fun should_notify_of_a_file_copied_event() {
        val copiedFile = myFixture.copyFileToProject(FILE_NAME, "$FILE_NAME.cp.py")
        val vFileEvent = VFileCopyEvent(null, file, file.parent, "$FILE_NAME.cp.py")

        clearInvocations(backendService)
        virtualFileSystemListener.after(listOf(vFileEvent))

        val paramsCaptor = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        val secondParamsCaptor = argumentCaptor<Boolean>()
        verify(backendService, timeout(3000)).updateFileSystem(paramsCaptor.capture(), secondParamsCaptor.capture())

        assertThat(paramsCaptor.allValues).hasSize(1)
        assertThat(paramsCaptor.firstValue.values).hasSize(1)
        paramsCaptor.firstValue.values.forEach {
            assertThat(it[0].type).isEqualTo(ModuleFileEvent.Type.CREATED)
            assertThat(it[0].virtualFile).isEqualTo(copiedFile)
            assertThat(it[0].virtualFile.name).isEqualTo("$FILE_NAME.cp.py")
        }
        assertThat(secondParamsCaptor.firstValue).isFalse()
    }

    @Test
    fun should_notify_of_a_file_deleted_event_before_a_file_is_moved() {
        val vFileEvent = VFileMoveEvent(null, file, file.parent)

        clearInvocations(backendService)
        virtualFileSystemListener.before(listOf(vFileEvent))

        val paramsCaptor = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        val secondParamsCaptor = argumentCaptor<Boolean>()
        verify(backendService, timeout(3000)).updateFileSystem(paramsCaptor.capture(), secondParamsCaptor.capture())

        assertThat(paramsCaptor.allValues).hasSize(1)
        assertThat(paramsCaptor.firstValue.values).hasSize(1)
        paramsCaptor.firstValue.values.forEach {
            assertThat(it[0].type).isEqualTo(ModuleFileEvent.Type.DELETED)
            assertThat(it[0].virtualFile).isEqualTo(file)
        }
        assertThat(secondParamsCaptor.firstValue).isFalse()
    }

    @Test
    fun should_notify_of_a_file_created_event_after_a_file_is_moved() {
        val vFileEvent = VFileMoveEvent(null, file, file.parent)

        clearInvocations(backendService)
        virtualFileSystemListener.after(listOf(vFileEvent))

        val paramsCaptor = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        val secondParamsCaptor = argumentCaptor<Boolean>()
        verify(backendService, timeout(3000)).updateFileSystem(paramsCaptor.capture(), secondParamsCaptor.capture())

        assertThat(paramsCaptor.allValues).hasSize(1)
        assertThat(paramsCaptor.firstValue.values).hasSize(1)
        paramsCaptor.firstValue.values.forEach {
            assertThat(it[0].type).isEqualTo(ModuleFileEvent.Type.CREATED)
            assertThat(it[0].virtualFile).isEqualTo(file)
        }
        assertThat(secondParamsCaptor.firstValue).isFalse()
    }

    @Test
    fun should_not_notify_of_a_file_property_changed_event() {
        val vFileEvent = VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, "oldName", "newName", false)

        clearInvocations(backendService)
        virtualFileSystemListener.after(listOf(vFileEvent))

        verifyNoInteractions(backendService)
    }

    @Test
    fun should_not_notify_when_event_happens_on_an_empty_directory() {
        val directory = myFixture.tempDirFixture.findOrCreateDir("emptyDir")
        val vFileEvent = VFileDeleteEvent(null, directory, false)

        clearInvocations(backendService)
        virtualFileSystemListener.after(listOf(vFileEvent))

        verifyNoInteractions(backendService)
    }

    @Test
    fun should_notify_about_subfiles_when_event_happens_on_a_directory() {
        val directory = myFixture.copyDirectoryToProject("sub/folder", "sub/folder")
        val subfileName = "subfile.py"
        val childFile = directory.findChild(subfileName)!!
        val vFileEvent = VFileDeleteEvent(null, directory, false)

        clearInvocations(backendService)
        virtualFileSystemListener.before(listOf(vFileEvent))

        val paramsCaptor = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        val secondParamsCaptor = argumentCaptor<Boolean>()
        verify(backendService, timeout(3000)).updateFileSystem(paramsCaptor.capture(), secondParamsCaptor.capture())

        assertThat(paramsCaptor.allValues).hasSize(1)
        assertThat(paramsCaptor.firstValue.values).hasSize(1)
        paramsCaptor.firstValue.values.forEach {
            assertThat(it[0].type).isEqualTo(ModuleFileEvent.Type.DELETED)
            assertThat(it[0].virtualFile).isEqualTo(childFile)
        }
        assertThat(secondParamsCaptor.firstValue).isFalse()
    }

    private var projectBindingManager: ProjectBindingManager = mock()
    private lateinit var backendService: BackendService
    private lateinit var actualBackendService: BackendService
    private lateinit var file: VirtualFile
    private lateinit var virtualFileSystemListener: VirtualFileSystemListener

}
