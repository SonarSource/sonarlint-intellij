/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarsource.sonarlint.core.client.api.common.ClientModuleFileEvent
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

private const val FILE_NAME = "main.py"

@RunWith(MockitoJUnitRunner::class)
class VirtualFileSystemListenerTest : AbstractSonarLintLightTests() {
    @Before
    fun prepare() {
        replaceProjectService(ProjectBindingManager::class.java, projectBindingManager)
        `when`(projectBindingManager.engineIfStarted).thenReturn(fakeEngine)
        file = myFixture.copyFileToProject(FILE_NAME, FILE_NAME)
        virtualFileSystemListener = VirtualFileSystemListener()
    }

    @Test
    fun should_notify_engine_of_a_file_deleted_event() {
        val vFileEvent = VFileDeleteEvent(null, file, false)

        virtualFileSystemListener.after(listOf(vFileEvent))

        verify(fakeEngine).fireModuleFileEvent(ArgumentMatchers.eq(module), eventCaptor.capture())
        val event = eventCaptor.value
        assertThat(event.type()).isEqualTo(ModuleFileEvent.Type.DELETED)
        val inputFile = event.target()
        assertThat(inputFile.contents()).contains("content")
        assertThat(inputFile.relativePath()).isEqualTo(FILE_NAME)
        assertThat(inputFile.getClientObject<Any>() as VirtualFile).isEqualTo(file)
        assertThat(inputFile.path).isEqualTo("/src/main.py")
    }

    @Test
    fun should_notify_engine_of_a_file_modified_event() {
        val vFileEvent = VFileContentChangeEvent(null, file, 0L, 0L, false)

        virtualFileSystemListener.after(listOf(vFileEvent))

        verify(fakeEngine).fireModuleFileEvent(ArgumentMatchers.eq(module), eventCaptor.capture())
        val event = eventCaptor.value
        assertThat(event.type()).isEqualTo(ModuleFileEvent.Type.MODIFIED)
        val inputFile = event.target()
        assertThat(inputFile.contents()).contains("content")
        assertThat(inputFile.relativePath()).isEqualTo(FILE_NAME)
        assertThat(inputFile.getClientObject<Any>() as VirtualFile).isEqualTo(file)
        assertThat(inputFile.path).isEqualTo("/src/main.py")
    }

    @Test
    fun should_notify_engine_of_a_file_created_event() {
        val vFileEvent = VFileCreateEvent(null, file.parent, FILE_NAME, false, null, null, false, null)

        virtualFileSystemListener.after(listOf(vFileEvent))

        verify(fakeEngine).fireModuleFileEvent(ArgumentMatchers.eq(module), eventCaptor.capture())
        val event = eventCaptor.value
        assertThat(event.type()).isEqualTo(ModuleFileEvent.Type.CREATED)
        val inputFile = event.target()
        assertThat(inputFile.contents()).contains("content")
        assertThat(inputFile.relativePath()).isEqualTo(FILE_NAME)
        assertThat(inputFile.getClientObject<Any>() as VirtualFile).isEqualTo(file)
        assertThat(inputFile.path).isEqualTo("/src/main.py")
    }

    @Test
    fun should_notify_engine_of_a_file_copied_event() {
        val copiedFile = myFixture.copyFileToProject(FILE_NAME, "$FILE_NAME.cp")
        val vFileEvent = VFileCopyEvent(null, file, file.parent, "$FILE_NAME.cp")

        virtualFileSystemListener.after(listOf(vFileEvent))

        verify(fakeEngine).fireModuleFileEvent(ArgumentMatchers.eq(module), eventCaptor.capture())
        val event = eventCaptor.value
        assertThat(event.type()).isEqualTo(ModuleFileEvent.Type.CREATED)
        val inputFile = event.target()
        assertThat(inputFile.contents()).contains("content")
        assertThat(inputFile.relativePath()).isEqualTo("$FILE_NAME.cp")
        assertThat(inputFile.getClientObject<Any>() as VirtualFile).isEqualTo(copiedFile)
        assertThat(inputFile.path).isEqualTo("/src/main.py.cp")
    }

    @Test
    fun should_notify_engine_of_a_file_deleted_event_before_a_file_is_moved() {
        val vFileEvent = VFileMoveEvent(null, file, file.parent)

        virtualFileSystemListener.before(listOf(vFileEvent))

        verify(fakeEngine).fireModuleFileEvent(ArgumentMatchers.eq(module), eventCaptor.capture())
        val deletionEvent = eventCaptor.value
        assertThat(deletionEvent.type()).isEqualTo(ModuleFileEvent.Type.DELETED)
        val deletedInputFile = deletionEvent.target()
        assertThat(deletedInputFile.contents()).contains("content")
        assertThat(deletedInputFile.relativePath()).isEqualTo(FILE_NAME)
        assertThat(deletedInputFile.getClientObject<Any>() as VirtualFile).isEqualTo(file)
        assertThat(deletedInputFile.path).isEqualTo("/src/main.py")
    }

    @Test
    fun should_notify_engine_of_a_file_created_event_after_a_file_is_moved() {
        val vFileEvent = VFileMoveEvent(null, file, file.parent)

        virtualFileSystemListener.after(listOf(vFileEvent))

        verify(fakeEngine).fireModuleFileEvent(ArgumentMatchers.eq(module), eventCaptor.capture())
        val creationEvent = eventCaptor.value
        assertThat(creationEvent.type()).isEqualTo(ModuleFileEvent.Type.CREATED)
        val createdInputFile = creationEvent.target()
        assertThat(createdInputFile.contents()).contains("content")
        assertThat(createdInputFile.relativePath()).isEqualTo(FILE_NAME)
        assertThat(createdInputFile.getClientObject<Any>() as VirtualFile).isEqualTo(file)
        assertThat(createdInputFile.path).isEqualTo("/src/main.py")
    }

    @Test
    fun should_not_notify_engine_of_a_file_property_changed_event() {
        val vFileEvent = VFilePropertyChangeEvent(null, file, VirtualFile.PROP_NAME, "oldName", "newName", false)

        virtualFileSystemListener.after(listOf(vFileEvent))

        verifyZeroInteractions(fakeEngine)
    }

    @Test
    fun should_not_notify_engine_if_not_started() {
        `when`(projectBindingManager.engineIfStarted).thenReturn(null)
        val vFileEvent = VFileDeleteEvent(null, file, false)

        virtualFileSystemListener.after(listOf(vFileEvent))

        verifyZeroInteractions(fakeEngine)
    }

    @Test
    fun should_log_in_the_console_when_an_error_occurs_handling_the_event() {
        val vFileEvent = VFileDeleteEvent(null, file, false)
        doThrow(RuntimeException("Boom!")).`when`(fakeEngine)
            .fireModuleFileEvent(ArgumentMatchers.any(), ArgumentMatchers.any())

        virtualFileSystemListener.after(listOf(vFileEvent))

        assertThat(console.lastMessage).isEqualTo("Error notifying analyzer of a file event")
    }

    @Captor
    private lateinit var eventCaptor: ArgumentCaptor<ClientModuleFileEvent>

    @Mock
    private lateinit var projectBindingManager: ProjectBindingManager

    @Mock
    private lateinit var fakeEngine: SonarLintEngine
    private lateinit var file: VirtualFile
    private lateinit var virtualFileSystemListener: VirtualFileSystemListener
}
