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
package org.sonarlint.intellij.core

import com.intellij.openapi.project.Project
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import java.util.concurrent.CompletableFuture

class ServerEventsSubscriber {
    private val subscriptions: MutableList<Subscription> = mutableListOf()

    fun subscribeFor(project: Project) {
        val serverConnection = getServerConnection(project) ?: return
        val projectKeys = getBoundProjectKeys(project)
        val result = tryConnect(project, serverConnection, projectKeys) { getService(ServerEventHandler::class.java).handle(it) }
        if (result is Subscription) {
            subscriptions.add(result)
            SonarLintConsole.get(project).info("Connected to server event stream")
        }
    }

    private fun tryConnect(project: Project, serverConnection: ServerConnection, projectKeys: Set<String>, messageConsumer: (String) -> Unit): SubscriptionResult {
        return try {
            Subscription(serverConnection.subscribeForEvents(projectKeys, messageConsumer))
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Error connecting to server event stream", e)
            SubscriptionError(e.message ?: "Error")
        }
    }

    private fun getServerConnection(project: Project) =
        getService(project, ProjectBindingManager::class.java)
            .tryGetServerConnection().orElse(null)

    private fun getBoundProjectKeys(project: Project) =
        getService(project, ProjectBindingManager::class.java).uniqueProjectKeys

    private sealed interface SubscriptionResult

    private class Subscription(val future: CompletableFuture<Void>) : SubscriptionResult

    private class SubscriptionError(val cause: String) : SubscriptionResult
}
