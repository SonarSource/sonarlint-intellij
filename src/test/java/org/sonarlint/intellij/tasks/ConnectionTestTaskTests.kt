/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fixtures.newPartialConnection

class ConnectionTestTaskTests : AbstractSonarLintLightTests() {
    @Test
    fun should_mark_progress_indicator_as_indeterminate() {
        val task = ConnectionTestTask(newPartialConnection())
        val progress = mock(ProgressIndicator::class.java)

        task.run(progress)

        verify(progress).isIndeterminate = true
    }

    @Test
    fun should_not_validate_connection_when_host_does_not_exist() {
        val connection = newPartialConnection("invalid_url")
        val task = ConnectionTestTask(connection)

        task.run(mock(ProgressIndicator::class.java))

        val result = task.result!!
        assertThat(result.isSuccess).isFalse
        assertThat(result.message).isNotBlank
    }

    @Test
    fun should_return_no_result_if_task_has_been_canceled() {
        val connection = newPartialConnection()
        val progress = mock(ProgressIndicator::class.java)
        val backendService = mock(BackendService::class.java)
        replaceProjectService(BackendService::class.java, backendService)
        `when`(backendService.validateConnection(connection)).thenReturn(CompletableFuture())
        val task = ConnectionTestTask(connection)
        `when`(progress.checkCanceled()).thenThrow(ProcessCanceledException())

        task.run(progress)

        assertNull(task.result)
    }
}
