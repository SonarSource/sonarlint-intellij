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
package org.sonarlint.intellij.fs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.replaceService
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.VirtualFileUtils.toURI
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class VirtualFileSystemMoveTests : AbstractSonarLintHeavyTests() {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun should_preserve_original_uri_when_moving_within_a_module(directory: Boolean) {
        checkMove(directory, true, true)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun should_preserve_source_module_when_moving_to_another_module(directory: Boolean) {
        checkMove(directory, true)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun should_notify_only_deletion_when_moving_outside_content(directory: Boolean) {
        checkMove(directory, false)
    }

    private fun checkMove(directory: Boolean, insideContent: Boolean, sameModule: Boolean = false) {
        val sourceRoot = createTestProjectStructure()
        val destination = createTestProjectStructure()
        ModuleRootModificationUtil.addContentRoot(module, sourceRoot)
        val destinationModule = if (insideContent) (if (sameModule) module else createModule("destination")).also {
            ModuleRootModificationUtil.addContentRoot(it, destination)
        } else null
        val application = ApplicationManager.getApplication()
        val backend = mock<BackendService>()
        val tasks = ArrayDeque<Runnable>()
        val executor = object : AbstractExecutorService() {
            private var stopped = false
            override fun execute(command: Runnable) { tasks.addLast(command) }
            override fun shutdown() { stopped = true }
            override fun shutdownNow(): MutableList<Runnable> {
                stopped = true
                return tasks.toMutableList().also { tasks.clear() }
            }
            override fun isShutdown() = stopped
            override fun isTerminated() = stopped && tasks.isEmpty()
            override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
        }
        application.replaceService(BackendService::class.java, backend, testRootDisposable)
        application.replaceService(VirtualFileSystemEventsHandler::class.java, DefaultVirtualFileSystemEventsHandler(executor), testRootDisposable)
        lateinit var moving: com.intellij.openapi.vfs.VirtualFile
        lateinit var leaf: com.intellij.openapi.vfs.VirtualFile
        WriteCommandAction.runWriteCommandAction(project) {
            moving = if (directory) sourceRoot.createChildDirectory(this, "tree") else sourceRoot.createChildData(this, "file.py")
            leaf = if (directory) moving.createChildData(this, "leaf.py") else moving
        }
        tasks.clear()
        clearInvocations(backend)
        val originalUri = toURI(leaf)

        WriteCommandAction.runWriteCommandAction(project) { moving.move(this, destination) }
        while (tasks.isNotEmpty()) tasks.removeFirst().run()

        val batches = argumentCaptor<Map<Module, List<VirtualFileEvent>>>()
        // An outside-content move has no eligible creation batch.
        verify(backend, times(if (insideContent) 2 else 1)).updateFileSystem(batches.capture(), eq(false))
        assertThat(batches.firstValue.keys).containsExactly(module)
        val deletion = batches.firstValue.getValue(module).single()
        assertThat(deletion.type).isEqualTo(ModuleFileEvent.Type.DELETED)
        assertThat(deletion.uri).isEqualTo(originalUri)
        if (destinationModule != null) {
            assertThat(batches.secondValue.keys).containsExactly(destinationModule)
            val creation = batches.secondValue.getValue(destinationModule).single()
            assertThat(creation.type).isEqualTo(ModuleFileEvent.Type.CREATED)
            assertThat(creation.uri).isEqualTo(toURI(leaf))
        }
    }
}
