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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.trigger.TriggerType

class AnalysisStateTests : AbstractSonarLintLightTests() {

    private val file1 = Mockito.mock(VirtualFile::class.java)
    private val file2 = Mockito.mock(VirtualFile::class.java)
    private val callback = Mockito.mock(AnalysisCallback::class.java)
    private val progress = Mockito.mock(ProgressIndicator::class.java)
    private lateinit var randomUuid1: UUID
    private lateinit var randomUuid2: UUID

    @BeforeEach
    fun init() {
        randomUuid1 = UUID.randomUUID()
        randomUuid2 = UUID.randomUUID()
    }

    @Test
    fun should_find_redundant_analysis_for_non_snapshot() {
        val filesToAnalyze = mutableListOf(file1, file2)

        val analysisState = AnalysisState(randomUuid1, callback, filesToAnalyze, module, TriggerType.EDITOR_OPEN, progress)
        val analysisState2 = AnalysisState(randomUuid2, callback, filesToAnalyze, module, TriggerType.BINDING_UPDATE, progress)

        assertThat(analysisState.isRedundant(analysisState2)).isTrue()
        assertThat(analysisState2.isRedundant(analysisState)).isTrue()
    }

    @Test
    fun should_find_redundant_analysis_for_snapshot() {
        val filesToAnalyze = mutableListOf(file1, file2)

        val analysisState = AnalysisState(randomUuid1, callback, filesToAnalyze, module, TriggerType.ALL, progress)
        val analysisState2 = AnalysisState(randomUuid2, callback, filesToAnalyze, module, TriggerType.CHANGED_FILES, progress)

        assertThat(analysisState.isRedundant(analysisState2)).isTrue()
        assertThat(analysisState2.isRedundant(analysisState)).isTrue()
    }

    @Test
    fun should_not_find_redundant_analysis_for_different_types() {
        val filesToAnalyze = mutableListOf(file1, file2)

        val analysisState = AnalysisState(randomUuid1, callback, filesToAnalyze, module, TriggerType.EDITOR_OPEN, progress)
        val analysisState2 = AnalysisState(randomUuid2, callback, filesToAnalyze, module, TriggerType.CHANGED_FILES, progress)

        assertThat(analysisState.isRedundant(analysisState2)).isFalse()
        assertThat(analysisState2.isRedundant(analysisState)).isFalse()
    }

    @Test
    fun should_not_find_redundant_analysis_with_different_files() {
        val filesToAnalyze1 = mutableListOf(file1)
        val filesToAnalyze2 = mutableListOf(file2)


        val analysisState = AnalysisState(randomUuid1, callback, filesToAnalyze1, module, TriggerType.EDITOR_OPEN, progress)
        val analysisState2 = AnalysisState(randomUuid2, callback, filesToAnalyze2, module, TriggerType.EDITOR_CHANGE, progress)

        assertThat(analysisState.isRedundant(analysisState2)).isFalse()
        assertThat(analysisState2.isRedundant(analysisState)).isFalse()
    }

    @Test
    fun should_find_redundant_analysis_for_subset_files() {
        val filesToAnalyze1 = mutableListOf(file1)
        val filesToAnalyze2 = mutableListOf(file1, file2)


        val analysisState = AnalysisState(randomUuid1, callback, filesToAnalyze1, module, TriggerType.EDITOR_OPEN, progress)
        val analysisState2 = AnalysisState(randomUuid2, callback, filesToAnalyze2, module, TriggerType.EDITOR_CHANGE, progress)

        assertThat(analysisState.isRedundant(analysisState2)).isTrue()
        assertThat(analysisState2.isRedundant(analysisState)).isFalse()
    }

}
