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

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.EngineManager
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.ProjectLogOutput
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput
import java.util.Optional

interface ServerEventsService {
    fun autoSubscribe(binding: ProjectBinding) {
        Settings.getGlobalSettings().getServerConnectionByName(binding.connectionName).ifPresent { connection ->
            val engineManager = getService(EngineManager::class.java)
            autoSubscribe(
                engineManager.getConnectedEngineIfStarted(binding.connectionName),
                connection
            )
        }
    }

    fun autoSubscribe(engineIfStarted: ConnectedSonarLintEngine?, serverConnection: ServerConnection)
    fun unsubscribe(project: Project)
}

class ServerEventsProductionService : ServerEventsService {
    override fun autoSubscribe(engineIfStarted: ConnectedSonarLintEngine?, serverConnection: ServerConnection) {
        val openedProjects = openedProjects()
        engineIfStarted?.subscribeForEvents(
            serverConnection.endpointParams,
            serverConnection.httpClient,
            getProjectKeys(serverConnection, openedProjects),
            CompositeLogOutput(getLogOutputs(serverConnection, openedProjects))
        )
    }

    override fun unsubscribe(project: Project) {
        val bindingManager = bindingManager(project)
        val projectConnection = bindingManager.tryGetServerConnection().orElse(null)
        projectConnection?.let { connection ->
            val openedProjects = openedProjects().minus(project)
            bindingManager.validConnectedEngine?.subscribeForEvents(
                connection.endpointParams,
                connection.httpClient,
                getProjectKeys(connection, openedProjects),
                CompositeLogOutput(getLogOutputs(connection, openedProjects))
            )
        }
    }

    private fun getProjectKeys(serverConnection: ServerConnection, projects: Set<Project>) =
        projects
            .filter { bindingManager(it).tryGetServerConnection().equals(Optional.of(serverConnection)) }
            .flatMap { bindingManager(it).uniqueProjectKeys }
            .toSet()

    private fun getLogOutputs(serverConnection: ServerConnection, projects: Set<Project>) =
        projects
            .filter { bindingManager(it).tryGetServerConnection().equals(Optional.of(serverConnection)) }
            .map { ProjectLogOutput(it) }
            .toSet()

    private fun openedProjects() = ProjectManager.getInstance().openProjects.toSet()

    private fun bindingManager(project: Project): ProjectBindingManager =
        getService(project, ProjectBindingManager::class.java)

    private class CompositeLogOutput(private val outputs: Set<ClientLogOutput>) : ClientLogOutput {
        override fun log(formattedMessage: String, level: ClientLogOutput.Level) {
            outputs.forEach { it.log(formattedMessage, level) }
        }
    }
}
