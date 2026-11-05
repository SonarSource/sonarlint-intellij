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
package org.sonarlint.intellij.fixtures

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * A local web server for light tests, to stand in for SonarQube Server or Cloud while the real backend talks to it, as
 * in the language server's medium tests. Register it in a companion object:
 *
 * ```
 * @JvmField @RegisterExtension
 * val server = MockWebServerExtension.onRandomPort()
 * ```
 *
 * then answer the requests a test expects, e.g. `server.addStringResponse("/api/system/status", "{...}")`. A request
 * matches its path and query, or else its path alone; any other request gets 404 and is listed in [unansweredRequests].
 * [receivedRequests] lists every request, e.g. to check the token a client sent. Answers and requests are cleared after
 * each test. It uses the JDK's own HTTP server, so it needs no dependency.
 */
class MockWebServerExtension private constructor() : BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private lateinit var server: HttpServer
    private val responsesByPath = ConcurrentHashMap<String, MockResponse>()

    /** Requests that got no answer, e.g. "GET /api/plugins/installed", to tell what a failing test is missing. */
    val unansweredRequests: MutableList<String> = CopyOnWriteArrayList()

    /** Every request the server received, oldest first. */
    val receivedRequests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

    override fun beforeAll(context: ExtensionContext) {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange -> exchange.use { dispatch(it) } }
            start()
        }
    }

    override fun afterEach(context: ExtensionContext) {
        responsesByPath.clear()
        unansweredRequests.clear()
        receivedRequests.clear()
    }

    override fun afterAll(context: ExtensionContext) {
        server.stop(0)
    }

    /** The address of [path] on this server, e.g. `url("")` for the server itself. */
    fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

    fun addStringResponse(path: String, body: String) {
        addResponse(path, 200, body)
    }

    fun addResponse(path: String, code: Int, body: String = "", contentType: String = "application/json") {
        responsesByPath[path] = MockResponse(code, body, contentType)
    }

    private fun dispatch(exchange: HttpExchange) {
        val uri = exchange.requestURI
        val pathAndQuery = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
        receivedRequests.add(RecordedRequest(exchange.requestMethod, uri.rawPath, uri.rawQuery, exchange.requestHeaders.getFirst("Authorization")))
        val response = responsesByPath[pathAndQuery] ?: responsesByPath[uri.rawPath]
        if (response == null) {
            unansweredRequests.add("${exchange.requestMethod} $pathAndQuery")
            exchange.sendResponseHeaders(404, -1)
            return
        }
        val body = response.body.toByteArray()
        exchange.responseHeaders.add("Content-Type", response.contentType)
        exchange.sendResponseHeaders(response.code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) {
            exchange.responseBody.write(body)
        }
    }

    private data class MockResponse(val code: Int, val body: String, val contentType: String)

    /** A request the server received, with its `Authorization` header if any. */
    data class RecordedRequest(val method: String, val path: String, val query: String?, val authorization: String?)

    companion object {
        fun onRandomPort() = MockWebServerExtension()
    }
}
