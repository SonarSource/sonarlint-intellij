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

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import java.util.Timer
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

class ServerEventsSubscriber {
    private val subscriptions: MutableList<Subscription> = mutableListOf()

    // TODO update subscriptions when project binding changes (bound, unbound, module binding changed, ....)
    // ServerConnection could also change (e.g. after credentials update)
    fun subscribeFor(project: Project) {
        val serverConnection = getServerConnection(project) ?: return
        val projectKeys = getBoundProjectKeys(project)
        val serverSubscription = subscriptions.find { it.serverConnection == serverConnection }
        if (serverSubscription == null) {
            val subscription = Subscription(serverConnection, projectKeys)
            subscriptions += subscription
            subscription.connectAuto()
        } else {
            serverSubscription.extendSubscription(projectKeys)
        }
    }

    fun unsubscribeFor(project: Project) {
        val serverConnection = getServerConnection(project) ?: return
        val projectKeys = getBoundProjectKeys(project)

        val serverSubscription = subscriptions.find { it.serverConnection == serverConnection } ?: return
        serverSubscription.reduceSubscription(projectKeys)
        if (serverSubscription.projectKeys.isEmpty()) {
            subscriptions.remove(serverSubscription)
        }
    }

    private fun getServerConnection(project: Project) =
        getService(project, ProjectBindingManager::class.java)
            .tryGetServerConnection().orElse(null)

    private fun getBoundProjectKeys(project: Project) =
        getService(project, ProjectBindingManager::class.java).uniqueProjectKeys

    private class Subscription(
        val serverConnection: ServerConnection,
        var projectKeys: Set<String>
    ) {
        var future: Future<Void>? = null
        var heartBeatWatchdogTimer: Timer? = null

        fun extendSubscription(newProjectKeys: Set<String>) {
            resubscribe(projectKeys + newProjectKeys)
        }

        fun reduceSubscription(oldProjectKeys: Set<String>) {
            resubscribe(projectKeys - oldProjectKeys)
        }

        fun resubscribe(newProjectKeys: Set<String>) {
            if (projectKeys == newProjectKeys) return
            stop()
            projectKeys = newProjectKeys
            if (projectKeys.isNotEmpty()) {
                connectAuto()
            }
        }

        fun stop() {
            println("[POC] Disconnecting event stream from ${serverConnection.hostUrl}")
            future?.cancel(true)
            future = null
        }

        fun connectAuto() {
            restartHeartBeatWatchdog()
            future = try {
                serverConnection.subscribeForEvents(projectKeys) { handleMessage(it) }
                    .whenComplete { _, error -> if (error != null) retryLater() else connectAuto() }
            } catch (e: Exception) {
                println("[POC] Subscription failed, retrying in 1min")
                retryLater()
            }
        }

        private fun restartHeartBeatWatchdog() {
            stopHeartBeatWatchdog()
            heartBeatWatchdogTimer = Timer()
            heartBeatWatchdogTimer!!.schedule(1_000L * 60) {
                future?.cancel(true)
                retryLater()
            }
        }

        private fun stopHeartBeatWatchdog() {
            heartBeatWatchdogTimer?.cancel()
        }

        private fun retryLater(): Future<Void>? {
            stopHeartBeatWatchdog()
            future = executorService.schedule({ connectAuto() }, 1L, TimeUnit.MINUTES) as Future<Void>
            return future
        }

        private fun handleMessage(message: String) {
            restartHeartBeatWatchdog()
            val event = ServerEventParser.parse(message) ?: return
            getService(ServerEventHandler::class.java).handle(serverConnection, event)
        }

        companion object {
            private val executorService = Executors.newScheduledThreadPool(5)
        }
    }
}

object ServerEventParser {
    private const val DATA_PREFIX = "data: "
    private const val PAYLOAD_SUFFIX = "\r\n\r\n"

    fun parse(payload: String): Event? {
        if (isHeartBeat(payload)) return null
        val message = extractMessage(payload) ?: return null
        // only one event type for the moment
        return Gson().fromJson(message, RuleActivated::class.java)
    }

    private fun isHeartBeat(payload: String) =
        payload == "\r" || payload == "\n" || payload == "\r\n"

    private fun extractMessage(eventPayload: String): String? {
        return if (isValidSSEEvent(eventPayload)) {
            eventPayload.substring(DATA_PREFIX.length, eventPayload.length - PAYLOAD_SUFFIX.length)
        } else {
            println("Invalid event payload: $eventPayload")
            null
        }
    }

    private fun isValidSSEEvent(receivedData: String) =
        receivedData.startsWith(DATA_PREFIX) && receivedData.endsWith(PAYLOAD_SUFFIX)
}

interface Event

data class RuleActivated(val project: String, val type: String, val content: RuleActivationContent) : Event

data class RuleActivationContent(val params: Set<String>, val ruleKey: String)
