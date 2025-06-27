/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.lang.Language
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.timeout
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.any
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

@Disabled("Disabled as ProjectLocator returns an empty list of projects")
class EventSchedulerTests : AbstractSonarLintLightTests() {

    private val backend = mock(BackendService::class.java)

    @BeforeEach
    fun prepare() {
        replaceProjectService(BackendService::class.java, backend)
        globalSettings.isAutoTrigger = true
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            Assertions.assertThat(SonarLintUtils.getService(project, AnalysisReadinessCache::class.java).isModuleReady(module)).isTrue()
        }
        Mockito.clearInvocations(backend)
    }

    @Test
    fun should_trigger_single_file_analysis() {
        val eventScheduler = EventScheduler("testScheduler", 200, false)
        val file = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "")
        eventScheduler.notify(file)

        verify(backend, timeout(2000))
            .updateFileSystem(mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file))), true)
    }

    @Test
    fun should_trigger_multiple_file_analysis() {
        val eventScheduler = EventScheduler("testScheduler",  200, false)
        val file1 = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "")
        val file2 = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "")
        eventScheduler.notify(file1)
        eventScheduler.notify(file2)

        verify(backend, timeout(2000))
            .updateFileSystem(mapOf(
                module to listOf(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file1)),
                module to listOf(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file2))
            ), true)
    }

    @Test
    fun should_trigger_different_analysis_at_interval() {
        val eventScheduler = EventScheduler("testScheduler",  200, true)
        val file1 = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "")
        val file2 = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "")
        eventScheduler.notify(file1)
        Thread.sleep(250)
        eventScheduler.notify(file2)

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted {
            verify(backend, times(2)).updateFileSystem(any(), any())
        }
    }

    @Test
    fun should_trigger_single_analysis_without_interval() {
        val eventScheduler = EventScheduler("testScheduler", 200, false)
        val file1 = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "")
        val file2 = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "")
        eventScheduler.notify(file1)
        Thread.sleep(150)
        eventScheduler.notify(file2)
        Thread.sleep(150)
        eventScheduler.notify(file2)

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted {
            verify(backend, times(1)).updateFileSystem(mapOf(
                module to listOf(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file1)),
                module to listOf(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file2))
            ), true)
        }
    }

}
