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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.net.URI
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.SonarLintTestUtils.createIssue
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto

class AnalysisStateTests : AbstractSonarLintLightTests() {

    @Test
    fun cancellation_during_finding_conversion_does_not_publish_partial_results() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val uri = URI.create(myFixture.file.virtualFile.url)
        val callback = mock(AnalysisCallback::class.java)
        val analysisId = UUID.randomUUID()
        val state = AnalysisState(analysisId, callback, module)
        val status = mock(AnalysisStatus::class.java)
        replaceProjectService(AnalysisStatus::class.java, status)
        val tracker = RunningAnalysesTracker(project)
        replaceProjectService(RunningAnalysesTracker::class.java, tracker)
        tracker.track(state)
        val firstIssue = createIssue(1)
        org.mockito.Mockito.`when`(firstIssue.quickFixes).thenReturn(emptyList())
        val canceledIssue = mock(RaisedIssueDto::class.java)
        val indicator = EmptyProgressIndicator()
        val cancellation = ProcessCanceledException()
        doAnswer {
            indicator.cancel()
            throw cancellation
        }.`when`(canceledIssue).textRange

        assertThatThrownBy {
            ProgressManager.getInstance().runProcess(Runnable {
                state.addRawIssues(analysisId, mapOf(uri to listOf(firstIssue, canceledIssue)), false)
            }, indicator)
        }.isInstanceOf(ProcessCanceledException::class.java)

        assertThat(indicator.isCanceled).isTrue()
        assertThat(state.isAnalysisFinished()).isFalse()
        verify(callback).onError(cancellation)
        verify(callback, never()).onSuccess(any())
        verifyNoMoreInteractions(callback)
        verify(status).stopRun(analysisId)
        assertThat(tracker.getById(analysisId)).isNull()
    }

    @Test
    fun cancellation_during_hotspot_conversion_ends_analysis_without_publishing_results() {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val uri = URI.create(myFixture.file.virtualFile.url)
        val callback = mock(AnalysisCallback::class.java)
        val analysisId = UUID.randomUUID()
        val state = AnalysisState(analysisId, callback, module)
        val status = mock(AnalysisStatus::class.java)
        replaceProjectService(AnalysisStatus::class.java, status)
        val tracker = RunningAnalysesTracker(project)
        replaceProjectService(RunningAnalysesTracker::class.java, tracker)
        tracker.track(state)
        val canceledHotspot = mock(RaisedHotspotDto::class.java)
        val cancellation = ProcessCanceledException()
        doAnswer { throw cancellation }.`when`(canceledHotspot).textRange

        assertThatThrownBy {
            state.addRawHotspots(analysisId, mapOf(uri to listOf(canceledHotspot)), false)
        }.isSameAs(cancellation)

        assertThat(state.isAnalysisFinished()).isFalse()
        verify(callback).onError(cancellation)
        verify(callback, never()).onSuccess(any())
        verifyNoMoreInteractions(callback)
        verify(status).stopRun(analysisId)
        assertThat(tracker.getById(analysisId)).isNull()
    }
}
