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
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.mock
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.util.ServerEventsNoOpService
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine

class SubscribeOnConnectionEditTest : AbstractSonarLintLightTests() {
    @After
    fun cleanUp() {
        (getService(ServerEventsService::class.java) as ServerEventsNoOpService).clearSubscriptions()
    }

    @Test
    fun should_subscribe_when_connection_is_edited() {
        val previousGlobalSettings = SonarLintGlobalSettings()
        previousGlobalSettings.addServerConnection(
            ServerConnection.newBuilder().setName("connectionName").setToken("token1").build()
        )
        val engine = mock(ConnectedSonarLintEngine::class.java)
        engineManager.registerEngine(engine, "connectionName")
        val connection = ServerConnection.newBuilder().setName("connectionName").setToken("token2").build()
        connectProjectTo(connection, "projectKey")

        SubscribeOnConnectionEdit().applied(previousGlobalSettings, globalSettings)

        val service = getService(ServerEventsService::class.java) as ServerEventsNoOpService
        assertThat(service.subscriptions)
            .contains(Pair(engine, connection))
    }

    @Test
    fun should_not_subscribe_when_connection_not_edited() {
        val engine = mock(ConnectedSonarLintEngine::class.java)
        engineManager.registerEngine(engine, "connectionName")
        val connection = ServerConnection.newBuilder().setName("connectionName").build()
        connectProjectTo(connection, "projectKey")

        SubscribeOnConnectionEdit().applied(SonarLintGlobalSettings(), globalSettings)

        val service = getService(ServerEventsService::class.java) as ServerEventsNoOpService
        assertThat(service.subscriptions).isEmpty()
    }
}
