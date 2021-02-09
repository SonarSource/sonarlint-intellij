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

import org.apache.hc.client5.http.auth.CredentialsProvider
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.classic.methods.HttpDelete
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.util.Timeout
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.serverapi.HttpClient.Response
import java.io.IOException

class ApacheHttpClient private constructor(
  private val client: HttpClient,
  credentials: UsernamePasswordCredentials? = null
) : org.sonarsource.sonarlint.core.serverapi.HttpClient {

  private val authContext: HttpClientContext = HttpClientContext.create()
  private val proxyAuth = SystemDefaultCredentialsProvider()

  init {
    authContext.credentialsProvider = CredentialsProvider { authScope, context -> proxyAuth.getCredentials(authScope, context) ?: credentials }
  }

  fun withCredentials(credentials: UsernamePasswordCredentials?): ApacheHttpClient {
    return ApacheHttpClient(client, credentials)
  }

  override fun get(url: String): Response {
    return execute(HttpGet(url))
  }

  override fun post(url: String, contentType: String, body: String): Response {
    val httpPost = HttpPost(url)
    httpPost.entity = StringEntity(body, ContentType.parse(contentType))
    return execute(httpPost)
  }

  override fun delete(url: String, contentType: String, body: String): Response {
    val httpDelete = HttpDelete(url)
    httpDelete.entity = StringEntity(body, ContentType.parse(contentType))
    return execute(httpDelete)
  }

  private fun execute(httpRequest: HttpUriRequestBase): Response {
    try {
      val httpResponse = client.execute(httpRequest, authContext)
      return ApacheHttpResponse(httpRequest.requestUri, httpResponse as ClassicHttpResponse)
    } catch (e: IOException) {
      throw RuntimeException("Error processing HTTP request", e)
    }
  }

  companion object {
    private val CONNECTION_TIMEOUT = Timeout.ofSeconds(30)
    private val RESPONSE_TIMEOUT = Timeout.ofMinutes(10)

    @JvmStatic
    val default: ApacheHttpClient = ApacheHttpClient(
      HttpClients.custom()
        // let IntelliJ automatically manage proxy and ssl settings
        .useSystemProperties()
        .setUserAgent("SonarLint IntelliJ " + getService(SonarLintPlugin::class.java).version)
        .setDefaultRequestConfig(
          RequestConfig.copy(RequestConfig.DEFAULT)
            .setConnectionRequestTimeout(CONNECTION_TIMEOUT)
            .setResponseTimeout(RESPONSE_TIMEOUT)
            .build()
        )
        .build()
    )

  }
}