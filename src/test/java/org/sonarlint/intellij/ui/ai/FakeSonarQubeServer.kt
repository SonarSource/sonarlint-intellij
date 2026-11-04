/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.ai

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stands in for SonarQube Server, on a free local port, for the requests the real SonarQube CLI makes to sign in with a
 * token and to integrate an agent. It accepts only [VALID_TOKEN], and answers every other request with 404.
 */
class FakeSonarQubeServer : AutoCloseable {

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    val url: String = "http://127.0.0.1:${server.address.port}"

    /** Requests the fake did not answer, e.g. "GET /api/plugins/installed", to tell what a failing test was missing. */
    val unansweredRequests = CopyOnWriteArrayList<String>()

    init {
        server.createContext("/") { exchange -> exchange.use { respond(it) } }
        server.start()
    }

    private fun respond(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val response = when {
            path == "/api/authentication/validate" -> """{"valid":${token(exchange) == VALID_TOKEN}}"""
            path == "/api/users/current" -> """{"isLoggedIn":true,"login":"fake-user","name":"Fake User"}"""
            path == "/api/system/status" -> """{"id":"fake","version":"$VERSION","status":"UP"}"""
            path == "/api/server/version" -> VERSION
            // Not entitled to SonarQube agentic features: `sonar integrate` installs the rest
            path.startsWith("/api/v2/a3s/org-entitlement/") || path.startsWith("/api/v2/cag/cag-entitlement/") -> "{}"
            else -> null
        }
        if (response == null) {
            unansweredRequests.add("${exchange.requestMethod} $path")
            exchange.sendResponseHeaders(404, -1)
            return
        }
        val body = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.write(body)
    }

    /** The token of a bearer or basic authorization, as the CLI and the backend send it. */
    private fun token(exchange: HttpExchange): String? {
        val authorization = exchange.requestHeaders.getFirst("Authorization") ?: return null
        return when {
            authorization.startsWith("Bearer ") -> authorization.removePrefix("Bearer ")
            authorization.startsWith("Basic ") -> String(Base64.getDecoder().decode(authorization.removePrefix("Basic "))).substringBefore(':')
            else -> null
        }
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        const val VALID_TOKEN = "valid-fake-token"
        private const val VERSION = "2025.1.0.102418"
    }
}
