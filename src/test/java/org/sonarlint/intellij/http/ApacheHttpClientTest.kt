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

import com.intellij.util.net.HttpConfigurable
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class ApacheHttpClientTest : AbstractSonarLintLightTests() {
  private val proxy = Proxy()

  @Before
  fun clearIntelliJProxySettings() {
    val httpConfigurable = HttpConfigurable.getInstance()
    httpConfigurable.USE_HTTP_PROXY = false
    httpConfigurable.PROXY_HOST = null
    httpConfigurable.PROXY_PORT = 80
    httpConfigurable.PROXY_AUTHENTICATION = false
    httpConfigurable.proxyLogin = ""
    httpConfigurable.plainProxyPassword = ""
  }

  @After
  fun stopServer() {
    proxy.stop()
  }

  @Test
  fun can_connect_to_a_remote_server() {
    val response = ApacheHttpClient.default.get("http://google.com")

    assertThat(response.isSuccessful).isTrue()
  }

  @Test
  fun can_connect_through_proxy_without_authentication() {
    val port = getNextAvailablePort()
    proxy.start(port)

    val httpConfigurable = HttpConfigurable.getInstance()
    httpConfigurable.USE_HTTP_PROXY = true
    httpConfigurable.PROXY_HOST = "localhost"
    httpConfigurable.PROXY_PORT = port

    val response = ApacheHttpClient.default.get("http://google.com")

    assertThat(response.isSuccessful).isTrue()
    assertThat(response.bodyAsString()).isNotEmpty()
  }

  @Test
  fun can_connect_through_proxy_with_authentication() {
    val proxyUser = "user"
    val proxyPassword = "pass"
    val port = getNextAvailablePort()
    proxy.start(port, Proxy.Credentials(proxyUser, proxyPassword))

    val httpConfigurable = HttpConfigurable.getInstance()
    httpConfigurable.USE_HTTP_PROXY = true
    httpConfigurable.PROXY_HOST = "localhost"
    httpConfigurable.PROXY_PORT = port
    httpConfigurable.PROXY_AUTHENTICATION = true
    httpConfigurable.proxyLogin = proxyUser
    httpConfigurable.plainProxyPassword = proxyPassword

    val response = ApacheHttpClient.default.get("http://google.com")

    assertThat(response.isSuccessful).isTrue()
    assertThat(response.bodyAsString()).isNotEmpty()
  }

  @Test
  fun can_not_connect_through_proxy_when_authentication_not_provided_by_client() {
    val proxyUser = "user"
    val proxyPassword = "pass"
    val port = getNextAvailablePort()
    proxy.start(port, Proxy.Credentials(proxyUser, proxyPassword))

    val httpConfigurable = HttpConfigurable.getInstance()
    httpConfigurable.USE_HTTP_PROXY = true
    httpConfigurable.PROXY_HOST = "localhost"
    httpConfigurable.PROXY_PORT = port
    httpConfigurable.PROXY_AUTHENTICATION = true
    httpConfigurable.proxyLogin = proxyUser

    val response = ApacheHttpClient.default.get("http://google.com")

    assertThat(response.isSuccessful).isFalse()
  }

  companion object {

    private fun getNextAvailablePort(): Int {
      val address = InetAddress.getLocalHost()
      try {
        ServerSocket(0, 50, address).use { return it.localPort }
      } catch (e: IOException) {
        throw IllegalStateException("Fail to find an available port on $address", e)
      }
    }
  }
}