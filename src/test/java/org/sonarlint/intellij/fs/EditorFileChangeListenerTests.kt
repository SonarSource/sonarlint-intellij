/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.capture
import org.sonarlint.intellij.eq
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class EditorFileChangeListenerTests : AbstractSonarLintLightTests() {
    @Test
    fun should_notify_of_file_system_event_when_a_change_occurs_in_editor() {
        val fakeEngine = mock(SonarLintAnalysisEngine::class.java)
        val fakeNotifier = mock(ModuleFileEventsNotifier::class.java)

        val eventsCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<ClientModuleFileEvent>>
        getEngineManager().registerEngine(fakeEngine)
        val fileName = "file.py"
        val listener = EditorFileChangeListener(fakeNotifier)
        val file = myFixture.copyFileToProject(fileName, fileName)

        listener.afterDocumentChange(file.getDocument()!!)

        // wait for the notification to be delivered (because of the debounce delay)
        Awaitility.await().atMost(Duration.ofSeconds(7)).untilAsserted {
            verify(fakeNotifier, times(1)).notifyAsync(eq(fakeEngine), eq(module), capture(eventsCaptor))
        }

        val event = eventsCaptor.value[0]
        assertThat(event.type()).isEqualTo(ModuleFileEvent.Type.MODIFIED)
        val inputFile = event.target()
        assertThat(inputFile.contents()).contains("content")
        assertThat(inputFile.relativePath()).isEqualTo(fileName)
        assertThat(inputFile.getClientObject<Any>() as VirtualFile).isEqualTo(file)
        assertThat(inputFile.path).isEqualTo("/src/$fileName")
    }
}
