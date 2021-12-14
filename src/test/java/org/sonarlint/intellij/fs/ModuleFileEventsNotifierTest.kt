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

import org.assertj.core.api.Assertions
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.eq
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class ModuleFileEventsNotifierTest : AbstractSonarLintLightTests() {

    @Test
    fun should_notify_engine_of_events() {
        val event = ClientModuleFileEvent.of(mock(ClientInputFile::class.java), ModuleFileEvent.Type.MODIFIED)

        notifier.notify(engine, module, listOf(event))

        verify(engine).fireModuleFileEvent(eq(module), eq(event))
    }

    @Test
    fun should_log_in_the_console_when_an_error_occurs_handling_the_event() {
        val event = ClientModuleFileEvent.of(mock(ClientInputFile::class.java), ModuleFileEvent.Type.MODIFIED)
        Mockito.doThrow(RuntimeException("Boom!")).`when`(engine)
            .fireModuleFileEvent(ArgumentMatchers.any(), ArgumentMatchers.any())

        notifier.notify(engine, module, listOf(event))

        Assertions.assertThat(console.lastMessage).isEqualTo("Error notifying analyzer of a file event")
    }


    private val engine = mock(SonarLintEngine::class.java)
    private val notifier = ModuleFileEventsNotifier()
}
