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
package org.sonarlint.intellij.http

import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.proxy.CommonProxy
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http2.HttpVersionPolicy
import org.apache.hc.core5.reactor.ssl.TlsDetails
import org.apache.hc.core5.util.Timeout
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.commons.http.HttpClient.Response
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture


class ApacheHttpClient private constructor(
  private val client: CloseableHttpAsyncClient,
  private val login: String? = null,
  private val password: String? = null
) : org.sonarsource.sonarlint.core.commons.http.HttpClient {

  fun withCredentials(login: String?, password: String?): ApacheHttpClient {
    return ApacheHttpClient(client, login, password)
  }

  override fun get(url: String): Response {
    return getAsync(url).get()
  }

  override fun getAsync(url: String) = executeAsync(SimpleHttpRequests.get(url))

  override fun post(url: String, contentType: String, body: String): Response {
    val httpRequest = SimpleHttpRequests.post(url)
    httpRequest.setBody(body, ContentType.parse(contentType))
    return executeAsync(httpRequest).get()
  }

  override fun delete(url: String, contentType: String, body: String): Response {
    val httpRequest = SimpleHttpRequests.delete(url)
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
        // nothing to do, the completable future is already canceled
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
