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
package org.sonarlint.intellij.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings

class ServerConnectionsManagerTest : AbstractSonarLintLightTests() {
    private lateinit var backendService: BackendService
    private lateinit var manager: ServerConnectionsManager

    @Before
    fun prepare() {
        backendService = mock(BackendService::class.java)
        manager = ServerConnectionsManager(backendService)
    }

    @Test
    fun it_should_save_a_new_connection_in_global_settings_when_replacing() {
        val newSettings = SonarLintGlobalSettings()

        manager.replaceConnections(
            listOf(ServerConnection.newBuilder().setName("connectionId").build()), newSettings
        )

        assertThat(newSettings.serverConnections).hasSize(1)
    }

    @Test
    fun it_should_update_a_connection_in_global_settings_when_replacing() {
        globalSettings.serverConnections =
            listOf(ServerConnection.newBuilder().setName("connectionId").setHostUrl("host1").build())
        val newSettings = SonarLintGlobalSettings()

        manager.replaceConnections(
            listOf(
                ServerConnection.newBuilder().setName("connectionId").setHostUrl("host2").build()
            ), newSettings
        )

        assertThat(newSettings.serverConnections).extracting("name", "hostUrl")
            .containsOnly(tuple("connectionId", "host2"))
    }

    @Test
    fun it_should_remove_a_connection_from_global_settings_when_replacing() {
        globalSettings.serverConnections =
            listOf(ServerConnection.newBuilder().setName("connectionId").setHostUrl("host1").build())
        val newSettings = SonarLintGlobalSettings()

        manager.replaceConnections(emptyList(), newSettings)

        assertThat(newSettings.serverConnections).isEmpty()
    }

    @Test
    fun it_should_save_a_new_connection_in_global_settings_when_adding() {
        manager.addConnection(ServerConnection.newBuilder().setName("connectionId").build())

        assertThat(globalSettings.serverConnections).hasSize(1)
    }

    @Test
    fun it_should_notify_backend_of_a_new_connection_when_adding() {
        val newConnection = ServerConnection.newBuilder().setName("connectionId").build()

        manager.addConnection(newConnection)

        verify(backendService).connectionAdded(newConnection)
    }

    @Test
    fun it_should_notify_backend_of_a_new_connection_when_replacing() {
        val newConnection = ServerConnection.newBuilder().setName("connectionId").build()

        manager.replaceConnections(listOf(newConnection), SonarLintGlobalSettings())

        verify(backendService).connectionAdded(newConnection)
    }

    @Test
    fun it_should_notify_backend_of_a_removed_connection_when_replacing() {
        globalSettings.serverConnections =
            listOf(ServerConnection.newBuilder().setName("connectionId").setHostUrl("host1").build())

        manager.replaceConnections(emptyList(), SonarLintGlobalSettings())

        verify(backendService).connectionRemoved("connectionId")
    }

    @Test
    fun it_should_not_notify_backend_of_untouched_connections_when_replacing() {
        val connection = ServerConnection.newBuilder().setName("connectionId1").setHostUrl("host1").build()
        globalSettings.serverConnections = listOf(connection)

        manager.replaceConnections(listOf(connection), SonarLintGlobalSettings())

        verifyNoInteractions(backendService)
    }

    @Test
    fun it_should_notify_backend_of_new_connection_but_not_untouched_when_replacing() {
        val existingConnection = ServerConnection.newBuilder().setName("connectionId1").setHostUrl("host1").build()
        globalSettings.serverConnections = listOf(existingConnection)
        val newConnection = ServerConnection.newBuilder().setName("connectionId2").setHostUrl("host1").build()

        manager.replaceConnections(listOf(existingConnection, newConnection), SonarLintGlobalSettings())

        verify(backendService).connectionAdded(newConnection)
        verifyNoMoreInteractions(backendService)
    }
}
