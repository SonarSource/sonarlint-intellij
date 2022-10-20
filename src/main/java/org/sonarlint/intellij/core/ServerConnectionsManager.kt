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

import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings

class ServerConnectionsManager(private val backendService: BackendService = getService(BackendService::class.java)) {
    fun replaceConnections(connections: List<ServerConnection>, newSettings: SonarLintGlobalSettings) {
        val previousConnections = getGlobalSettings().serverConnections

        newSettings.serverConnections = connections
        notifyConnectionChanges(previousConnections, connections)

    }

    fun addConnection(connection: ServerConnection) {
        getGlobalSettings().addServerConnection(connection)
        backendService.connectionAdded(connection)
    }

    private fun notifyConnectionChanges(
        previousConnections: Collection<ServerConnection>, newConnections: Collection<ServerConnection>
    ) {
        val previousConnectionsByName = previousConnections.associateBy { it.name }
        val newConnectionsByName = newConnections.associateBy { it.name }
        val editedConnections =
            newConnectionsByName.filterValues { previousConnectionsByName.containsKey(it.name) }.values
                .filter { previousConnectionsByName[it.name] != it }
        val addedConnections =
            newConnectionsByName.filterValues { !previousConnectionsByName.containsKey(it.name) }.values
        val removedConnectionNames = previousConnectionsByName.keys.minus(newConnectionsByName.keys)
        removedConnectionNames.forEach { connectionId ->
            backendService.connectionRemoved(
                connectionId
            )
        }
        editedConnections.forEach { updatedConnection ->
            backendService.connectionUpdated(
                updatedConnection
            )
        }
        addedConnections.forEach { createdConnection ->
            backendService.connectionAdded(
                createdConnection
            )
        }
    }
}
