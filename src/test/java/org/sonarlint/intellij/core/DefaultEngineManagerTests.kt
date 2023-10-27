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
package org.sonarlint.intellij.core

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.fixtures.newSonarQubeConnection
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine

internal class DefaultEngineManagerTests : AbstractSonarLintLightTests() {
    private lateinit var manager: DefaultEngineManager
    private lateinit var engineFactory: SonarLintEngineFactory
    private var notifications: SonarLintProjectNotifications? = null
    private var connectedEngine: ConnectedSonarLintEngine? = null
    private var standaloneEngine: StandaloneSonarLintEngine? = null

    @BeforeEach
    fun before() {
        engineFactory = Mockito.mock(SonarLintEngineFactory::class.java)
        notifications = Mockito.mock(SonarLintProjectNotifications::class.java)
        connectedEngine = Mockito.mock(ConnectedSonarLintEngine::class.java)
        standaloneEngine = Mockito.mock(StandaloneSonarLintEngine::class.java)

        Mockito.`when`(engineFactory.createEngine(ArgumentMatchers.anyString(), ArgumentMatchers.eq(false))).thenReturn(connectedEngine)
        Mockito.`when`(engineFactory.createEngine()).thenReturn(standaloneEngine)

        manager = DefaultEngineManager(engineFactory)
        setServerConnections(emptyList())
    }

    @Test
    fun should_get_standalone() {
        Assertions.assertThat(manager.standaloneEngine).isEqualTo(standaloneEngine)
        Assertions.assertThat(manager.standaloneEngine).isEqualTo(standaloneEngine)
        Mockito.verify(engineFactory, Mockito.times(1)).createEngine()
    }

    @Test
    fun should_get_connected() {
        setServerConnections(listOf(createConnection("server1")))

        Assertions.assertThat(manager.getConnectedEngine("server1")).isEqualTo(connectedEngine)
        Assertions.assertThat(manager.getConnectedEngine("server1")).isEqualTo(connectedEngine)
        Mockito.verify(engineFactory, Mockito.times(1)).createEngine("server1", false)
    }

    @Test
    fun should_fail_invalid_server() {
        val throwable = Assertions.catchThrowable { manager.getConnectedEngine(notifications, "server1", "project1") }

        Assertions.assertThat(throwable)
                .isInstanceOf(InvalidBindingException::class.java)
                .hasMessage("Invalid server name: server1")
    }

    private fun createConnection(name: String): ServerConnection {
        return newSonarQubeConnection(name)
    }
}
