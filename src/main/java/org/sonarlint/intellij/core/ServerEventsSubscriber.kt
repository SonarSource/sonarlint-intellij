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

    // TODO update subscriptions when project binding changes (bound, unbound, module binding changed, ....)
    fun subscribeFor(project: Project) {
        val serverConnection = getServerConnection(project) ?: return
        val projectKeys = getBoundProjectKeys(project)
        val serverSubscription = subscriptions.find { it.serverConnection == serverConnection }
        if (serverSubscription == null) {
            val result = tryConnect(project, serverConnection, projectKeys) {
                getService(ServerEventHandler::class.java).handle(it)
            }
            if (result is Subscription) {
                subscriptions.add(result)
            }
        } else {
            serverSubscription.extendSubscription(projectKeys) {
                getService(ServerEventHandler::class.java).handle(it)
            }
        }
    }

    fun unsubscribeFor(project: Project) {
        val serverConnection = getServerConnection(project) ?: return
        val projectKeys = getBoundProjectKeys(project)

        val serverSubscription = subscriptions.find { it.serverConnection == serverConnection } ?: return
        serverSubscription.reduceSubscription(projectKeys) {
            getService(ServerEventHandler::class.java).handle(it)
        }
        if (serverSubscription.projectKeys.isEmpty()) {
            subscriptions.remove(serverSubscription)
        }
    }

    private fun tryConnect(
        project: Project,
        serverConnection: ServerConnection,
        projectKeys: Set<String>,
        messageConsumer: (String) -> Unit
    ): SubscriptionResult {
        return try {
            Subscription(
                serverConnection,
                projectKeys,
                serverConnection.subscribeForEvents(projectKeys, messageConsumer)
            )
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Error connecting to server event stream", e)
            SubscriptionError()
        }
    }

    private fun getServerConnection(project: Project) =
        getService(project, ProjectBindingManager::class.java)
            .tryGetServerConnection().orElse(null)

    private fun getBoundProjectKeys(project: Project) =
        getService(project, ProjectBindingManager::class.java).uniqueProjectKeys

    private sealed interface SubscriptionResult

    private class Subscription(
        val serverConnection: ServerConnection,
        var projectKeys: Set<String>,
        var future: CompletableFuture<Void>
    ) : SubscriptionResult {
        fun extendSubscription(newProjectKeys: Set<String>, messageConsumer: (String) -> Unit) {
            resubscribe(projectKeys + newProjectKeys, messageConsumer)
        }

        fun reduceSubscription(oldProjectKeys: Set<String>, messageConsumer: (String) -> Unit) {
            resubscribe(projectKeys - oldProjectKeys, messageConsumer)
        }

        fun resubscribe(newProjectKeys: Set<String>, messageConsumer: (String) -> Unit) {
            if (projectKeys != newProjectKeys) {
                stop()
            }
            if (newProjectKeys.isNotEmpty()) {
                future = serverConnection.subscribeForEvents(projectKeys, messageConsumer)
            }
            projectKeys = newProjectKeys
        }

        fun stop() {
            println("[POC] Disconnecting event stream from ${serverConnection.hostUrl}")
            future.cancel(true)
        }
    }

    private class SubscriptionError : SubscriptionResult
}
