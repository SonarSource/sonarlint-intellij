/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.commons.http.HttpClient
import org.sonarsource.sonarlint.core.serverapi.EndpointParams
import java.util.concurrent.CompletableFuture

class ConnectionTestTaskTest : AbstractSonarLintLightTests() {
    @Test
    fun should_mark_progress_indicator_as_indeterminate() {
        val task = ConnectionTestTask(ServerConnection.newBuilder().setHostUrl("invalid_url").build())
        val progress = mock(ProgressIndicator::class.java)

        task.run(progress)

        verify(progress).isIndeterminate = true
    }

    @Test
    fun should_not_validate_connection_when_host_does_not_exist() {
        val server = ServerConnection.newBuilder().setHostUrl("invalid_url").build()
        val task = ConnectionTestTask(server)

        task.run(mock(ProgressIndicator::class.java))

        val result = task.result!!
        assertThat(result.success()).isFalse
        assertThat(result.message()).isNotBlank
    }

    @Test
    fun should_return_no_result_if_task_has_been_canceled() {
        val progress = mock(ProgressIndicator::class.java)
        val server = mock(ServerConnection::class.java)
        `when`(server.endpointParams).thenReturn(EndpointParams("base", false, null))
        val httpClient = mock(HttpClient::class.java)
        `when`(httpClient.getAsync(ArgumentMatchers.any())).thenReturn(CompletableFuture())
        `when`(server.httpClient).thenReturn(httpClient)
        val task = ConnectionTestTask(server)
        GlobalScope.launch {
            delay(1000)
            `when`(progress.isCanceled).thenReturn(true)
        }

        task.run(progress)

        assertThat(task.result).isNull()
    }
}
