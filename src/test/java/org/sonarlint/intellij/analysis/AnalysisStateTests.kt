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

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.net.URI
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.SonarLintIntelliJClient
import org.sonarlint.intellij.SonarLintTestUtils.createIssue
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThreadAndWait
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
        assertThat(tracker.isCanceled(analysisId)).isTrue()
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
        assertThat(tracker.isCanceled(analysisId)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun canceled_analysis_drops_later_issue_and_hotspot_publications(cancelHotspot: Boolean) {
        myFixture.configureByText("Sample.java", "class Sample {}")
        val uri = URI.create(myFixture.file.virtualFile.url)
        val analysisId = UUID.randomUUID()
        val callback = mock(AnalysisCallback::class.java)
        val tracker = RunningAnalysesTracker(project)
        replaceProjectService(RunningAnalysesTracker::class.java, tracker)
        replaceProjectService(AnalysisStatus::class.java, mock(AnalysisStatus::class.java))
        tracker.track(AnalysisState(analysisId, callback, module))
        val holder = mock(OnTheFlyFindingsHolder::class.java)
        val submitter = mock(AnalysisSubmitter::class.java)
        whenever(submitter.onTheFlyFindingsHolder).thenReturn(holder)
        replaceProjectService(AnalysisSubmitter::class.java, submitter)
        val scopeId = BackendService.moduleId(module)
        val cancellation = ProcessCanceledException()

        runOnUiThreadAndWait(project, ModalityState.defaultModalityState()) {
            assertThatThrownBy {
                if (cancelHotspot) {
                    val hotspot = mock(RaisedHotspotDto::class.java)
                    whenever(hotspot.textRange).thenThrow(cancellation)
                    SonarLintIntelliJClient.raiseHotspots(scopeId, mapOf(uri to listOf(hotspot)), true, analysisId)
                } else {
                    val issue = mock(RaisedIssueDto::class.java)
                    whenever(issue.textRange).thenThrow(cancellation)
                    SonarLintIntelliJClient.raiseIssues(scopeId, mapOf(uri to listOf(issue)), true, analysisId)
                }
            }.isSameAs(cancellation)
        }

        for (intermediate in listOf(true, false)) {
            SonarLintIntelliJClient.raiseIssues(scopeId, mapOf(uri to emptyList()), intermediate, analysisId)
            SonarLintIntelliJClient.raiseHotspots(scopeId, mapOf(uri to emptyList()), intermediate, analysisId)
        }
        verifyNoInteractions(holder)
        verify(callback).onError(cancellation)
        verifyNoMoreInteractions(callback)
        assertThat(tracker.isEmpty()).isTrue()

        // Backend-triggered analyses and updates outside an analysis must still reach the live views.
        for (otherId in listOf(null, UUID.randomUUID())) {
            SonarLintIntelliJClient.raiseIssues(scopeId, mapOf(uri to emptyList()), false, otherId)
            SonarLintIntelliJClient.raiseHotspots(scopeId, mapOf(uri to emptyList()), false, otherId)
        }
        verify(holder, times(2)).updateViewsWithNewIssues(module, mapOf(uri to emptyList()), false)
        verify(holder, times(2)).updateViewsWithNewSecurityHotspots(module, mapOf(uri to emptyList()), false)
    }
}
