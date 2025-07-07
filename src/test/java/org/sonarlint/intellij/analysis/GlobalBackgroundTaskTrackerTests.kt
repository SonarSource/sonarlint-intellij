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
package org.sonarlint.intellij.analysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.tasks.GlobalTaskProgressReporter

class GlobalBackgroundTaskTrackerTests {
    private lateinit var tracker: GlobalBackgroundTaskTracker
    private lateinit var reporter1: GlobalTaskProgressReporter
    private lateinit var reporter2: GlobalTaskProgressReporter

    @BeforeEach
    fun setUp() {
        tracker = GlobalBackgroundTaskTracker()
        reporter1 = mock(GlobalTaskProgressReporter::class.java)
        reporter2 = mock(GlobalTaskProgressReporter::class.java)
    }

    @Test
    fun `should track and untrack global tasks`() {
        tracker.track(reporter1)
        tracker.track(reporter2)
        tracker.untrackGlobalTask(reporter1)

        assertThat(true).isTrue()
    }

    @Test
    fun `should finish task by taskId`() {
        val taskId = "id1"
        tracker.track(reporter1)
        tracker.track(reporter2)
        // reporter1 has the taskId, reporter2 does not
        `when`(reporter1.hasTaskId(taskId)).thenReturn(true)
        `when`(reporter2.hasTaskId(taskId)).thenReturn(false)

        tracker.finishTask(taskId)

        verify(reporter1, times(1)).taskFinished(taskId)
        verify(reporter2, never()).taskFinished(taskId)
    }

    @Test
    fun `should check if taskId is cancelled`() {
        val taskId = "cancelledId"
        tracker.track(reporter1)
        tracker.track(reporter2)
        `when`(reporter1.getCancelledTaskIds()).thenReturn(setOf(taskId))
        `when`(reporter2.getCancelledTaskIds()).thenReturn(emptySet())

        assertThat(tracker.isTaskIdCancelled(taskId)).isTrue()
        assertThat(tracker.isTaskIdCancelled("otherId")).isFalse()
    }
}
