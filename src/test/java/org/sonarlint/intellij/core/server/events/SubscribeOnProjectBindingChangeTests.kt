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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.messages.PROJECT_BINDING_TOPIC
import org.sonarlint.intellij.util.ServerEventsNoOpService
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine

internal class SubscribeOnProjectBindingChangeTests : AbstractSonarLintLightTests() {

    @AfterEach
    fun cleanUp() {
        (getService(ServerEventsService::class.java) as ServerEventsNoOpService).clearSubscriptions()
    }

    @Test
    fun should_subscribe_to_new_server_when_binding_project() {
        val engine = mock(ConnectedSonarLintEngine::class.java)
        engineManager.registerEngine(engine, "connectionName")
        val connection = ServerConnection.newBuilder().setName("connectionName").build()
        connectProjectTo(connection, "projectKey")

        project.messageBus.syncPublisher(PROJECT_BINDING_TOPIC)
            .bindingChanged(null, ProjectBinding("connectionName", "projectKey", emptyMap()))

        val service = getService(ServerEventsService::class.java) as ServerEventsNoOpService
        assertThat(service.subscriptions)
            .contains(Pair(engine, connection))
    }

    @Test
    fun should_resubscribe_to_previous_server_when_unbinding_project() {
        val engine = mock(ConnectedSonarLintEngine::class.java)
        engineManager.registerEngine(engine, "connectionName")
        val connection = ServerConnection.newBuilder().setName("connectionName").build()
        connectProjectTo(connection, "projectKey")

        project.messageBus.syncPublisher(PROJECT_BINDING_TOPIC)
            .bindingChanged(ProjectBinding("connectionName", "projectKey", emptyMap()), null)

        val service = getService(ServerEventsService::class.java) as ServerEventsNoOpService
        assertThat(service.subscriptions)
            .contains(Pair(engine, connection))
    }
}
