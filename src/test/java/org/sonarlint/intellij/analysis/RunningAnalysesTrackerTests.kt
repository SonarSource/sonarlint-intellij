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

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService

class RunningAnalysesTrackerTests : AbstractSonarLintLightTests() {
    private lateinit var tracker: RunningAnalysesTracker
    private lateinit var backendService: BackendService
    private lateinit var analysisStatus: AnalysisStatus

    @BeforeEach
    fun setup() {
        backendService = mock(BackendService::class.java)
        analysisStatus = mock(AnalysisStatus::class.java)
        replaceApplicationService(BackendService::class.java, backendService)
        replaceProjectService(AnalysisStatus::class.java, analysisStatus)
        tracker = RunningAnalysesTracker(project)
    }

    @Test
    fun `should track and retrieve analysis state`() {
        val id = UUID.randomUUID()
        val state = mock(AnalysisState::class.java)
        `when`(state.id).thenReturn(id)

        tracker.track(state)

        assertThat(tracker.getById(id)).isEqualTo(state)
    }

    @Test
    fun `should finish analysis and remove from tracker`() {
        val id = UUID.randomUUID()
        val state = mock(AnalysisState::class.java)
        `when`(state.id).thenReturn(id)

        tracker.track(state)
        tracker.finish(state)

        verify(analysisStatus).stopRun(id)
        assertThat(tracker.getById(id)).isNull()
    }

    @Test
    fun `should report empty state correctly`() {
        assertThat(tracker.isEmpty()).isTrue()
        val id = UUID.randomUUID()
        val state = mock(AnalysisState::class.java)
        `when`(state.id).thenReturn(id)

        tracker.track(state)

        assertThat(tracker.isEmpty()).isFalse()
    }

    @Test
    fun `should cancel all analyses and clear tracker`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val state1 = mock(AnalysisState::class.java)
        val state2 = mock(AnalysisState::class.java)
        `when`(state1.id).thenReturn(id1)
        `when`(state2.id).thenReturn(id2)

        tracker.track(state1)
        tracker.track(state2)
        tracker.cancelAll()

        verify(backendService).cancelTask(id1.toString())
        verify(backendService).cancelTask(id2.toString())
        assertThat(tracker.isEmpty()).isTrue()
    }
} 
