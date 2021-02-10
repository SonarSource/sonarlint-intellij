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
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.DefaultHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Paths
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private const val SERVER_KEYSTORE = "server.p12"
private const val SERVER_KEYSTORE_PASSWORD = "pwdServerP12"
private const val SERVER_TRUSTSTORE_WITH_CLIENT_CA = "server-with-client-ca.p12"
private const val SERVER_TRUSTSTORE_WITH_CLIENT_CA_PASSWORD = "pwdServerWithClientCA"

class SSLTest : AbstractSonarLintLightTests() {
  @Before
  fun removeCertificatesFromStore() {
    val trustManager = CertificateManager.getInstance().customTrustManager
    trustManager.certificates.forEach { trustManager.removeCertificate(it) }
  }

  @After
  fun stopProxy() {
    if (server != null && server!!.isStarted) {
      server!!.stop()
    }
  }

  @Test
  fun can_connect_to_https_server_with_custom_certificate() {
    val httpsPort = startSSLTransparentReverseProxy(false)

    // IntelliJ automatically accepts the server certificate when in unit test mode
    val response = ApacheHttpClient.default.post("https://localhost:$httpsPort/echo", "text/plain", "Hello")

    assertThat(response.isSuccessful).isTrue
    assertThat(response.bodyAsString()).isEqualTo("Hello")
  }

  // This keystore contains only the server certificate
  private var server: Server? = null

  private fun startSSLTransparentReverseProxy(requireClientAuth: Boolean): Int {
    val httpPort: Int = getNextAvailablePort()
    val httpsPort = getNextAvailablePort()

    // Setup Threadpool
    val threadPool = QueuedThreadPool()
    threadPool.maxThreads = 500
    server = Server(threadPool)

    // HTTP Configuration
    val httpConfig = HttpConfiguration()
    httpConfig.secureScheme = "https"
    httpConfig.securePort = httpsPort
    httpConfig.sendServerVersion = true
    httpConfig.sendDateHeader = false

    // Handler Structure
    server!!.handler = HandlerCollection(proxyHandler(), DefaultHandler())
    val http = ServerConnector(server, HttpConnectionFactory(httpConfig))
    http.port = httpPort
    server!!.addConnector(http)
    val serverKeyStore = Paths.get("$testDataPath/$SERVER_KEYSTORE").toAbsolutePath()
    assertThat(serverKeyStore).exists()

    // SSL Context Factory
    val sslContextFactory = SslContextFactory.Server()
    sslContextFactory.keyStorePath = serverKeyStore.toString()
    sslContextFactory.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD)
    sslContextFactory.setKeyManagerPassword(SERVER_KEYSTORE_PASSWORD)
    if (requireClientAuth) {
      val serverTrustStore = Paths.get("$testDataPath/$SERVER_TRUSTSTORE_WITH_CLIENT_CA").toAbsolutePath()
      sslContextFactory.trustStorePath = serverTrustStore.toString()
      assertThat(serverTrustStore).exists()
      sslContextFactory.setTrustStorePassword(SERVER_TRUSTSTORE_WITH_CLIENT_CA_PASSWORD)
    }
    sslContextFactory.needClientAuth = requireClientAuth
    sslContextFactory.setExcludeCipherSuites(
      "SSL_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_DSS_WITH_DES_CBC_SHA",
      "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
      "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
    )

    // SSL HTTP Configuration
    val httpsConfig = HttpConfiguration(httpConfig)

    // SSL Connector
    val sslConnector = ServerConnector(
      server,
      SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
      HttpConnectionFactory(httpsConfig)
    )
    sslConnector.port = httpsPort
    server!!.addConnector(sslConnector)
    server!!.start()
    return httpsPort
  }

  private fun proxyHandler(): ServletContextHandler {
    val contextHandler = ServletContextHandler()
    contextHandler.servletHandler = newServletHandler()
    return contextHandler
  }

  private fun newServletHandler(): ServletHandler {
    val handler = ServletHandler()
    handler.addServletWithMapping(EchoServlet::class.java, "/echo")
    return handler
  }

  private fun getNextAvailablePort(): Int {
    val address = InetAddress.getLocalHost()
    try {
      ServerSocket(0, 50, address).use { return it.localPort }
    } catch (e: IOException) {
      throw IllegalStateException("Fail to find an available port on $address", e)
    }
  }
}

class EchoServlet : HttpServlet() {

  override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    try {
      resp.writer.write(req.reader.readText())
//    resp.outputStream.flush()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

}