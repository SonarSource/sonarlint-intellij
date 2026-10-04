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
package org.sonarlint.intellij.trigger

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class EventSchedulerDispatchTests : AbstractSonarLintLightTests() {

    private val backend = mock<BackendService>()
    private val projectLocator = mock<ProjectLocator>()

    @BeforeEach
    fun prepare() {
        replaceApplicationService(BackendService::class.java, backend)
        whenever(projectLocator.getProjectsForFile(any())).thenReturn(listOf(project))
    }

    @Test
    fun `dispatcher batches modified files with content per module`() {
        val firstFile = createTestFile("FirstClass.java", "class FirstClass {}")
        val secondFile = createTestFile("SecondClass.java", "class SecondClass {}")

        clearInvocations(backend)
        dispatchFileChanges(setOf(firstFile, secondFile), projectLocator)

        val events = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        verify(backend).updateFileSystem(events.capture(), eq(true))
        assertThat(events.firstValue.keys).containsExactly(module)
        assertThat(events.firstValue.getValue(module)).containsExactlyInAnyOrder(modified(firstFile), modified(secondFile))
    }

    @Test
    fun `production dispatcher rechecks a project closed after its first file was routed`() {
        val firstFile = createTestFile("FirstClass.java", "class FirstClass {}")
        val secondFile = createTestFile("SecondClass.java", "class SecondClass {}")
        val closed = AtomicBoolean()
        val closingProject = projectWithDisposalState(closed)
        val routedFiles = AtomicInteger()
        whenever(projectLocator.getProjectsForFile(any())).thenAnswer {
            if (routedFiles.incrementAndGet() == 2) closed.set(true)
            listOf(closingProject)
        }

        clearInvocations(backend)
        dispatchFileChanges(setOf(firstFile, secondFile), projectLocator)

        assertThat(routedFiles.get()).isEqualTo(2)
        verify(backend, never()).updateFileSystem(any(), any())
    }

    @Test
    fun `production dispatcher rechecks project disposal immediately before sending`() {
        val file = createTestFile("MyClass.java", "class MyClass {}")
        val closed = AtomicBoolean()
        val closingProject = projectWithDisposalState(closed)
        val realFileIndex = ProjectFileIndex.getInstance(project)
        val fileIndex = Mockito.mock(ProjectFileIndex::class.java, delegatesTo<Any>(realFileIndex))
        doReturn(fileIndex).`when`(closingProject).getService(ProjectFileIndex::class.java)
        doAnswer {
            realFileIndex.getModuleForFile(file, false).also { closed.set(true) }
        }.`when`(fileIndex).getModuleForFile(file, false)
        whenever(projectLocator.getProjectsForFile(file)).thenReturn(listOf(closingProject))

        clearInvocations(backend)
        dispatchFileChanges(setOf(file), projectLocator)

        assertThat(closed.get()).isTrue()
        verify(backend, never()).updateFileSystem(any(), any())
    }

    private fun projectWithDisposalState(closed: AtomicBoolean): Project {
        val closingProject = Mockito.mock(Project::class.java, delegatesTo<Any>(project))
        doAnswer { closed.get() }.`when`(closingProject).isDisposed
        return closingProject
    }

    private fun modified(file: VirtualFile) = VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file)
}
