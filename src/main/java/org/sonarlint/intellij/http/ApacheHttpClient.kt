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
package org.sonarlint.intellij.http

import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.proxy.CommonProxy
import org.apache.hc.client5.http.async.methods.AbstractCharResponseConsumer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.nio.support.BasicRequestProducer
import org.apache.hc.core5.http2.HttpVersionPolicy
import org.apache.hc.core5.reactor.ssl.TlsDetails
import org.apache.hc.core5.util.Timeout
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.commons.http.HttpClient
import org.sonarsource.sonarlint.core.commons.http.HttpClient.Response
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Consumer

class ApacheHttpClient private constructor(
    private val client: CloseableHttpAsyncClient,
    private val login: String? = null,
    private val password: String? = null
) : HttpClient {

    fun withCredentials(login: String?, password: String?): ApacheHttpClient {
        return ApacheHttpClient(client, login, password)
    }

    override fun get(url: String): Response {
        return getAsync(url).get()
    }

    override fun getAsync(url: String) = executeAsync(SimpleRequestBuilder.get(url).build())

    override fun getEventStream(
        url: String,
        connectionListener: HttpConnectionListener,
        messageConsumer: Consumer<String>
    ): HttpClient.AsyncRequest {
        val request = SimpleRequestBuilder.get(url).build()
        request.config = RequestConfig.custom()
            .setConnectionRequestTimeout(STREAM_CONNECTION_REQUEST_TIMEOUT)
            .setConnectTimeout(STREAM_CONNECTION_TIMEOUT)
            // infinite timeout, rely on heart beat check
            .setResponseTimeout(Timeout.ZERO_MILLISECONDS)
            .build()
        login?.let { request.setHeader("Authorization", basic(login, password ?: "")) }
        request.setHeader("Accept", "text/event-stream")
        var connected = false
        val httpFuture = client.execute(
            BasicRequestProducer(request, null),
            object : AbstractCharResponseConsumer<Nothing?>() {

                override fun start(
                    response: HttpResponse,
                    contentType: ContentType
                ) {
                    if (response.code < 200 || response.code >= 300) {
                        connectionListener.onError(response.code)
                    } else {
                        connected = true
                        connectionListener.onConnected()
                    }
                }

                override fun capacityIncrement() = Int.MAX_VALUE

                override fun data(data: CharBuffer, endOfStream: Boolean) {
                    if (connected) {
                        messageConsumer.accept(data.toString())
                    }
                }

                override fun buildResult() = null

                override fun failed(cause: java.lang.Exception) {
                    // log: might be internal error (e.g. in event handling) or disconnection from server
                    // notification of listener will happen in the FutureCallback
                    println(cause)
                }

                override fun releaseResources() {
                    // should we close something ?
                }
            }, object : FutureCallback<Nothing?> {
                override fun completed(unused: Nothing?) {
                    if (connected) {
                        connectionListener.onClosed()
                    }
                }

                override fun failed(ex: java.lang.Exception) {
                    if (connected) {
                        // called when disconnected from server
                        connectionListener.onClosed()
                    } else {
                        connectionListener.onError(null)
                    }
                }

                override fun cancelled() {
                    // nothing to do, the completable future is already canceled
                }

            }
        )
        return ApacheAsyncRequest(httpFuture)
    }

    class ApacheAsyncRequest(
        private val httpFuture: Future<Nothing?>,
    ) : HttpClient.AsyncRequest {
        override fun cancel() {
            httpFuture.cancel(true)
        }
    }

    override fun post(url: String, contentType: String, body: String): Response {
        val httpRequest = SimpleRequestBuilder.post(url).build()
        httpRequest.setBody(body, ContentType.parse(contentType))
        return executeAsync(httpRequest).get()
    }

    override fun delete(url: String, contentType: String, body: String): Response {
        val httpRequest = SimpleRequestBuilder.delete(url).build()
        httpRequest.setBody(body, ContentType.parse(contentType))
        return executeAsync(httpRequest).get()
    }

    private fun executeAsync(httpRequest: SimpleHttpRequest): CompletableFuture<Response> {
        login?.let { httpRequest.setHeader("Authorization", basic(login, password ?: "")) }
        val futureResponse = CompletableFuture<Response>()
        val httpFuture = client.execute(httpRequest, object : FutureCallback<SimpleHttpResponse> {
            override fun completed(result: SimpleHttpResponse) {
                futureResponse.complete(ApacheHttpResponse(httpRequest.requestUri, result))
            }

            override fun failed(ex: Exception) {
                futureResponse.completeExceptionally(ex)
            }

            override fun cancelled() {
                futureResponse.cancel(true)
            }
        })
        return futureResponse.whenComplete { _, error ->
            if (error is CancellationException) {
                httpFuture.cancel(false)
            }
        }
    }

    fun basic(username: String, password: String): String {
        val usernameAndPassword = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(usernameAndPassword.toByteArray(StandardCharsets.ISO_8859_1))
        return "Basic $encoded"
    }

    fun close() {
        client.close()
    }

    companion object {
        private val STREAM_CONNECTION_REQUEST_TIMEOUT = Timeout.ofSeconds(10)
        private val STREAM_CONNECTION_TIMEOUT = Timeout.ofMinutes(1)
        private val CONNECTION_TIMEOUT = Timeout.ofSeconds(30)
        private val RESPONSE_TIMEOUT = Timeout.ofMinutes(10)

        @JvmStatic
        val default: ApacheHttpClient = ApacheHttpClient(
            HttpAsyncClients.custom()
                .setConnectionManager(
                    PoolingAsyncClientConnectionManagerBuilder.create()
                        .setTlsStrategy(
                            ClientTlsStrategyBuilder.create()
                                .setSslContext(CertificateManager.getInstance().sslContext)
                                .setTlsDetailsFactory { TlsDetails(it.session, it.applicationProtocol) }
                                .build())
                        .build()
                )
                .setUserAgent("SonarLint IntelliJ " + getService(SonarLintPlugin::class.java).version)
                // SLI-629 - Force HTTP/1
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)

                // proxy settings
                .setRoutePlanner(SystemDefaultRoutePlanner(CommonProxy.getInstance()))
                .setDefaultCredentialsProvider(SystemDefaultCredentialsProvider())

                .setDefaultRequestConfig(
                    RequestConfig.copy(RequestConfig.DEFAULT)
                        .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
                        .setResponseTimeout(RESPONSE_TIMEOUT)
                        .build()
                )
                .build()
        )

        init {
            default.client.start()
        }
    }
}
