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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.trigger.EventScheduler

class EditorFileChangeListenerTests : AbstractSonarLintLightTests() {

    private val scheduler = mock<EventScheduler>()
    private lateinit var listener: EditorFileChangeListener
    private lateinit var file: VirtualFile
    private lateinit var document: Document

    @BeforeEach
    fun prepare() {
        listener = EditorFileChangeListener.createForTests(scheduler)
        Disposer.register(testRootDisposable, listener)
        file = myFixture.copyFileToProject("file.py", "file.py")
        myFixture.configureFromExistingVirtualFile(file)
        document = myFixture.editor.document
    }

    @Test
    fun `explicit startup forwards an editor edit once`() {
        listener.startListening()

        editDocument()

        verify(scheduler).notify(file)
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `repeated startup forwards an editor edit once`() {
        repeat(3) { listener.startListening() }

        editDocument()

        verify(scheduler).notify(file)
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `startup after an edit still forwards one notification per edit`() {
        listener.startListening()
        editDocument()
        verify(scheduler).notify(file)

        listener.startListening()
        editDocument()

        verify(scheduler, times(2)).notify(file)
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `concurrent startups forward an editor edit once`() {
        val workerCount = 8
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(workerCount)
        try {
            val registrations = (1..workerCount).map {
                workers.submit {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    listener.startListening()
                }
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            registrations.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            workers.shutdownNow()
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }

        editDocument()

        verify(scheduler).notify(file)
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `disposal removes the listener and stops its scheduler`() {
        listener.startListening()

        Disposer.dispose(listener)
        editDocument()

        verify(scheduler).stopScheduler()
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `startup after disposal does not register again`() {
        listener.startListening()
        Disposer.dispose(listener)

        listener.startListening()
        editDocument()

        verify(scheduler).stopScheduler()
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `disposal before initial startup prevents registration`() {
        Disposer.dispose(listener)

        listener.startListening()
        editDocument()

        verify(scheduler).stopScheduler()
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `application service is available with a public noarg constructor`() {
        assertThat(EditorFileChangeListener::class.java.getConstructor()).isNotNull()
        assertThat(ApplicationManager.getApplication().getService(EditorFileChangeListener::class.java)).isNotNull()
    }

    private fun editDocument() {
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "\n# edit") }
    }
}
