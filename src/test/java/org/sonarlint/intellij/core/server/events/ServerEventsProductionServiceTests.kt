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
package org.sonarlint.intellij.core.server.events

import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.any
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.eq
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine

class ServerEventsProductionServiceTests : AbstractSonarLintLightTests() {
    private val service = ServerEventsProductionService()

    @Test
    fun should_subscribe_when_project_bound_and_engine_started() {
        val engine = mock(ConnectedSonarLintEngine::class.java)
        engineManager.registerEngine(engine, "connectionName")
        val connection =
            ServerConnection.newBuilder().setName("connectionName").setHostUrl("url").setToken("token").build()
        connectProjectTo(connection, "projectKey")

        service.autoSubscribe(ProjectBinding("connectionName", "projectKey", emptyMap()))

        verify(engine).subscribeForEvents(any(), any(), eq(setOf("projectKey")), any(), any())
    }

    @Test
    fun should_unsubscribe_for_project() {
        val engine = mock(ConnectedSonarLintEngine::class.java)
        val connection =
            ServerConnection.newBuilder().setName("connectionName").setHostUrl("url").setToken("token").build()
        connectProjectTo(connection, "projectKey")
        engineManager.registerEngine(engine, "connectionName")
        service.autoSubscribe(engine, connection)
        clearInvocations(engine)

        service.unsubscribe(project)

        verify(engine).subscribeForEvents(any(), any(), eq(emptySet()), any(), any())
    }
}
