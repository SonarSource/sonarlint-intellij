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

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests

class GlobalTaskProgressReporterTests : AbstractSonarLintLightTests() {
    private lateinit var reporter: GlobalTaskProgressReporter
    private lateinit var module1: Module
    private lateinit var module2: Module
    private val totalTasks = 2

    @BeforeEach
    fun initialization() {
        module1 = mock(Module::class.java)
        module2 = mock(Module::class.java)
        reporter = GlobalTaskProgressReporter(project, "Test Global Task", totalTasks, true)
        val indicator = mock(ProgressIndicator::class.java)
        reporter.progressIndicator = indicator
    }

    @Test
    fun `should track and finish tasks correctly`() {
        reporter.trackTask(module1, "task1")
        assertThat(reporter.hasTaskId("task1")).isTrue()
        reporter.trackTask(module2, "task2")
        assertThat(reporter.hasTaskId("task2")).isTrue()
        reporter.taskFinished("task1")
        assertThat(reporter.hasTaskId("task1")).isFalse()
        reporter.taskFinished("task2")
        assertThat(reporter.hasTaskId("task2")).isFalse()
    }

    @Test
    fun `should handle cancellation and return cancelled task ids`() {
        reporter.trackTask(module1, "task1")
        reporter.trackTask(module2, "task2")
        reporter.onCancel()

        val cancelled = reporter.getCancelledTaskIds()

        assertThat(cancelled).containsExactlyInAnyOrder("task1", "task2")
    }

    @Test
    fun `should update text on progress indicator`() {
        val indicator = reporter.progressIndicator!!

        reporter.updateText("Some progress")

        verify(indicator).text = "Some progress"
    }

    @Test
    fun `should cancel all tasks when cancelAllTasks is called`() {
        reporter.trackTask(module1, "task1")
        reporter.trackTask(module2, "task2")

        reporter.cancelAllTasks()

        val cancelled = reporter.getCancelledTaskIds()
        assertThat(cancelled).containsExactlyInAnyOrder("task1", "task2")
    }

    @Test
    fun `should mark as cancelled when cancelAllTasks is called`() {
        reporter.trackTask(module1, "task1")

        reporter.cancelAllTasks()

        val cancelled = reporter.getCancelledTaskIds()
        assertThat(cancelled).isNotEmpty
        assertThat(reporter.getCancelledTaskIds()).contains("task1")
    }

}
