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

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.util.VirtualFileUtils

class AnalysisStateTests : AbstractSonarLintLightTests() {

    @Test
    fun should_put_submitted_files_on_the_result_even_when_findings_omit_them() {
        val fileA = createAndOpenTestVirtualFile("A.java", "class A {}")
        val fileB = createAndOpenTestVirtualFile("B.java", "class B {}")
        val callback = mock<AnalysisCallback>()
        val analysisId = UUID.randomUUID()
        val state = AnalysisState(analysisId, callback, module, listOf(fileA, fileB))
        val uriA = VirtualFileUtils.toURI(fileA)
        assertThat(uriA).isNotNull

        state.addRawHotspots(analysisId, emptyMap(), isIntermediate = false)
        state.addRawIssues(analysisId, mapOf(uriA!! to emptyList()), isIntermediate = false)

        verify(callback).onSuccess(check { result ->
            assertThat(result.analyzedFiles).containsExactlyInAnyOrder(fileA, fileB)
            assertThat(result.findings.issuesPerFile.keys).containsExactly(fileA)
            assertThat(result.findings.issuesPerFile[fileA]).isEmpty()
        })
    }

    @Test
    fun should_fall_back_to_findings_keys_when_no_submitted_files_were_passed() {
        val fileA = createAndOpenTestVirtualFile("A.java", "class A {}")
        val callback = mock<AnalysisCallback>()
        val analysisId = UUID.randomUUID()
        val state = AnalysisState(analysisId, callback, module)
        val uriA = VirtualFileUtils.toURI(fileA)
        assertThat(uriA).isNotNull

        state.addRawHotspots(analysisId, emptyMap(), isIntermediate = false)
        state.addRawIssues(analysisId, mapOf(uriA!! to emptyList()), isIntermediate = false)

        verify(callback).onSuccess(check { result ->
            assertThat(result.analyzedFiles).containsExactly(fileA)
        })
    }
}
