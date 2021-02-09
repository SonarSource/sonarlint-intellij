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
import org.eclipse.jetty.proxy.ProxyServlet
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
import org.junit.Ignore
import org.junit.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Paths

@Ignore("Not fully working")
class SSLTest : AbstractSonarLintLightTests() {
  @Before
  fun removeCertificatesFromStore() {
    val trustManager = CertificateManager.getInstance().customTrustManager
//    trustManager.certificates.forEach { trustManager.removeCertificate(it) }
  }

  @After
  fun stopProxy() {
    if (server != null && server!!.isStarted) {
      server!!.stop()
    }
  }

  @Test
  fun simple_analysis_with_server_and_client_certificate() {
    startSSLTransparentReverseProxy(false)

    val customTrustManager = CertificateManager.getInstance().customTrustManager
    customTrustManager.addCertificate("$testDataPath/ca.crt")
//    customTrustManager.addCertificate("$testDataPath/ca-client-auth.crt")
//    customTrustManager.addCertificate("$testDataPath/client.pem")
//    customTrustManager.addCertificate("$testDataPath/server.crt")
    customTrustManager.addCertificate("$testDataPath/server.pem")

    val response = ApacheHttpClient.default.get("https://localhost:$httpsPort/")

    assertThat(response.isSuccessful).isTrue()
  }

//  @Test
//  fun simple_analysis_with_server_and_client_certificate() {
//    startSSLTransparentReverseProxy(true)
//    val scanner = SimpleScanner()
//    var buildResult: BuildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort)
//    assertThat(buildResult.getLastStatus()).isNotEqualTo(0)
//    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException")
//    val clientTruststore = Paths.get(SSLTest::class.java.getResource(KEYSTORE_CLIENT_WITH_CA).toURI()).toAbsolutePath()
//    assertThat(clientTruststore).exists()
//    val clientKeystore = Paths.get(SSLTest::class.java.getResource(CLIENT_KEYSTORE).toURI()).toAbsolutePath()
//    assertThat(clientKeystore).exists()
//    val params: MutableMap<String, String> = HashMap()
//    // In the truststore we have the CA allowing to connect to local TLS server
//    params["javax.net.ssl.trustStore"] = clientTruststore.toString()
//    params["javax.net.ssl.trustStorePassword"] = CLIENT_WITH_CA_KEYSTORE_PASSWORD
//    // The KeyStore is storing the certificate to identify the user
//    params["javax.net.ssl.keyStore"] = clientKeystore.toString()
//    params["javax.net.ssl.keyStorePassword"] = CLIENT_KEYSTORE_PASSWORD
//    buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort, params)
//    assertThat(buildResult.getLastStatus()).isEqualTo(0)
//  }
//
//  @Test
//  fun simple_analysis_with_server_and_without_client_certificate_is_failing() {
//    startSSLTransparentReverseProxy(true)
//    val scanner = SimpleScanner()
//    var buildResult: BuildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort)
//    assertThat(buildResult.getLastStatus()).isNotEqualTo(0)
//    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException")
//    val clientTruststore = Paths.get(SSLTest::class.java.getResource(KEYSTORE_CLIENT_WITH_CA).toURI()).toAbsolutePath()
//    assertThat(clientTruststore).exists()
//    val clientKeystore = Paths.get(SSLTest::class.java.getResource(CLIENT_KEYSTORE).toURI()).toAbsolutePath()
//    assertThat(clientKeystore).exists()
//    val params: MutableMap<String, String> = HashMap()
//    // In the truststore we have the CA allowing to connect to local TLS server
//    params["javax.net.ssl.trustStore"] = clientTruststore.toString()
//    params["javax.net.ssl.trustStorePassword"] = CLIENT_WITH_CA_KEYSTORE_PASSWORD
//    // Voluntary missing client keystore
//    buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort, params)
//    assertThat(buildResult.getLastStatus()).isEqualTo(1)
//
//    // different exception is thrown depending on the JDK version. See: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8172163
//    assertThat(buildResult.getLogs())
//      .matches({ p ->
//        (p.matches(
//          "(?s).*org\\.sonarsource\\.scanner\\.api\\.internal\\.ScannerException: Unable to execute SonarScanner analysis.*" +
//            "Caused by: javax\\.net\\.ssl\\.SSLException: Broken pipe \\(Write failed\\).*"
//        )
//          ||
//          p.matches(
//            "(?s).*org\\.sonarsource\\.scanner\\.api\\.internal\\.ScannerException: Unable to execute SonarScanner analysis.*" +
//              "Caused by: javax\\.net\\.ssl\\.SSLProtocolException: Broken pipe \\(Write failed\\).*"
//          )
//          ||
//          p.matches(
//            "(?s).*org\\.sonarsource\\.scanner\\.api\\.internal\\.ScannerException: Unable to execute SonarScanner analysis.*" +
//              "Caused by: javax\\.net\\.ssl\\.SSLHandshakeException: Received fatal alert: bad_certificate.*"
//          )
//          ||
//          p.matches(
//            "(?s).*org\\.sonarsource\\.scanner\\.api\\.internal\\.ScannerException: Unable to execute SonarScanner analysis.*" +
//              "Caused by: java\\.net\\.SocketException: Broken pipe \\(Write failed\\).*" +
//              "java\\.base/sun\\.security\\.ssl\\.SSLSocketOutputRecord.*"
//          ))
//      })
//  }
//
//  @Test
//  fun simple_analysis_with_server_certificate_using_ca_in_truststore() {
//    simple_analysis_with_server_certificate(KEYSTORE_CLIENT_WITH_CA, CLIENT_WITH_CA_KEYSTORE_PASSWORD)
//  }
//
//  @Test
//  fun simple_analysis_with_server_certificate_using_server_certificate_in_truststore() {
//    simple_analysis_with_server_certificate(KEYSTORE_CLIENT_WITH_CERTIFICATE, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD)
//  }
//
//  private fun simple_analysis_with_server_certificate(clientTrustStore: String, keyStorePassword: String) {
//    startSSLTransparentReverseProxy(false)
//    val scanner = SimpleScanner()
//    var buildResult: BuildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort)
//    assertThat(buildResult.getLastStatus()).isNotEqualTo(0)
//    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException")
//    val clientTruststore = Paths.get(SSLTest::class.java.getResource(clientTrustStore).toURI()).toAbsolutePath()
//    assertThat(clientTruststore).exists()
//    val params: MutableMap<String, String> = HashMap()
//    params["javax.net.ssl.trustStore"] = clientTruststore.toString()
//    params["javax.net.ssl.trustStorePassword"] = keyStorePassword
//    buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort, params)
//    assertThat(buildResult.getLastStatus()).isEqualTo(0)
//  }

  // This keystore contains only the CA used to sign the server certificate
  private val KEYSTORE_CLIENT_WITH_CA = "/SSLTest/client-with-ca.p12"
  private val CLIENT_WITH_CA_KEYSTORE_PASSWORD = "pwdClientCAP12"

  // This keystore contains only the server certificate
  private val KEYSTORE_CLIENT_WITH_CERTIFICATE = "/SSLTest/client-with-certificate.p12"
  private val CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD = "pwdClientP12"
  private val SERVER_KEYSTORE = "server.p12"
  private val SERVER_KEYSTORE_PASSWORD = "pwdServerP12"
  private val SERVER_TRUSTSTORE_WITH_CLIENT_CA = "server-with-client-ca.p12"
  private val SERVER_TRUSTSTORE_WITH_CLIENT_CA_PASSWORD = "pwdServerWithClientCA"
  private val CLIENT_KEYSTORE = "/SSLTest/client.p12"
  private val CLIENT_KEYSTORE_PASSWORD = "pwdClientCertP12"
  private var server: Server? = null
  private var httpsPort = 0

  private fun startSSLTransparentReverseProxy(requireClientAuth: Boolean) {
    val httpPort: Int = getNextAvailablePort()
    httpsPort = getNextAvailablePort()

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
  }

  private fun proxyHandler(): ServletContextHandler {
    val contextHandler = ServletContextHandler()
    contextHandler.servletHandler = newServletHandler()
    return contextHandler
  }

  private fun newServletHandler(): ServletHandler {
    val handler = ServletHandler()
    handler.addServletWithMapping(ProxyServlet.Transparent::class.java, "/*")
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