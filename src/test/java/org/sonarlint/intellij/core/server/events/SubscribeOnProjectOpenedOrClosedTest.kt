/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.ProjectManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.mock
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.util.ServerEventsNoOpService
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine

class SubscribeOnProjectOpenedOrClosedTest : AbstractSonarLintLightTests() {
    @After
    fun cleanUp() {
        (getService(ServerEventsService::class.java) as ServerEventsNoOpService).clearSubscriptions()
    }

    @Test
    fun should_subscribe_when_project_is_opened() {
        val engine = mock(ConnectedSonarLintEngine::class.java)
        engineManager.registerEngine(engine, "connectionName")
        val connection = ServerConnection.newBuilder().setName("connectionName").build()
        connectProjectTo(connection, "projectKey")

        getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectOpened(project)

        val service = getService(ServerEventsService::class.java) as ServerEventsNoOpService
        assertThat(service.subscriptions)
            .contains(Pair(engine, connection))
    }
}
