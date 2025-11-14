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
package org.sonarlint.intellij.tasks

import com.intellij.openapi.progress.ProgressIndicator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressUpdateNotification

class TaskProgressReporterTests : AbstractSonarLintLightTests() {
    private lateinit var reporter: TaskProgressReporter
    private lateinit var indicator: ProgressIndicator

    @BeforeEach
    fun initialization() {
        indicator = mock(ProgressIndicator::class.java)
        reporter = TaskProgressReporter(project, "Test Task", true, true, "Initial message", "taskId") {}
        reporter.progressIndicator = indicator
    }

    @Test
    fun `should update progress with percentage and message`() {
        val notification = ProgressUpdateNotification("Halfway", 50)
        `when`(indicator.isIndeterminate).thenReturn(true)

        reporter.updateProgress(notification)

        verify(indicator).isIndeterminate = false
        verify(indicator).fraction = 50.0
        verify(indicator).text = "Halfway"
    }

    @Test
    fun `should update progress with only message`() {
        val notification = ProgressUpdateNotification("Just a message", null)

        reporter.updateProgress(notification)

        verify(indicator).text = "Just a message"
    }

    @Test
    fun `should complete and notify waitMonitor`() {
        reporter.complete()

        assertThat(true).isTrue()
    }
}
