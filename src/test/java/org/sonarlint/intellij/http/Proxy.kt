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

import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.security.ConstraintMapping
import org.eclipse.jetty.security.ConstraintSecurityHandler
import org.eclipse.jetty.security.HashLoginService
import org.eclipse.jetty.security.SecurityHandler
import org.eclipse.jetty.security.ServerAuthException
import org.eclipse.jetty.security.UserAuthentication
import org.eclipse.jetty.security.UserStore
import org.eclipse.jetty.security.authentication.DeferredAuthentication
import org.eclipse.jetty.security.authentication.LoginAuthenticator
import org.eclipse.jetty.server.Authentication
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.UserIdentity
import org.eclipse.jetty.server.handler.DefaultHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.util.B64Code
import org.eclipse.jetty.util.security.Constraint
import org.eclipse.jetty.util.security.Credential
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class Proxy {
  private lateinit var server: Server

  fun start(port: Int, credentials: Credentials? = null) {
    val threadPool = QueuedThreadPool()
    threadPool.maxThreads = 500

    server = Server(threadPool)

    val httpConfig = HttpConfiguration()
    httpConfig.secureScheme = "https"
    httpConfig.sendServerVersion = true
    httpConfig.sendDateHeader = false

    server.handler = HandlerCollection(proxyHandler(credentials), DefaultHandler())

    val http = ServerConnector(server, HttpConnectionFactory(httpConfig))
    http.port = port
    server.addConnector(http)
    server.start()
  }

  fun stop() {
    if (::server.isInitialized) {
      server.stop()
    }
  }

  private fun proxyHandler(credentials: Credentials?): ServletContextHandler {
    val contextHandler = ServletContextHandler()
    if (credentials != null) {
      contextHandler.securityHandler = basicAuth(
        credentials.username,
        credentials.password
      )
    }
    contextHandler.servletHandler = newServletHandler()
    return contextHandler
  }

  private fun basicAuth(username: String, password: String): SecurityHandler {
    val loginService = HashLoginService("Private!")
    val userStore = UserStore()
    userStore.addUser(username, Credential.getCredential(password), arrayOf("user"))
    loginService.setUserStore(userStore)
    val constraint = Constraint()
    constraint.name = Constraint.__BASIC_AUTH
    constraint.roles = arrayOf("user")
    constraint.authenticate = true
    val cm = ConstraintMapping()
    cm.constraint = constraint
    cm.pathSpec = "/*"
    val csh = ConstraintSecurityHandler()
    csh.authenticator = ProxyAuthenticator()
    csh.realmName = "myrealm"
    csh.addConstraintMapping(cm)
    csh.loginService = loginService
    return csh
  }

  private fun newServletHandler(): ServletHandler {
    val handler = ServletHandler()
    handler.addServletWithMapping(MyProxyServlet::class.java, "/*")
    return handler
  }

  class MyProxyServlet : ProxyServlet() {
    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
      super.service(request, response)
    }
  }

  data class Credentials(val username: String, val password: String)

  class ProxyAuthenticator  /* ------------------------------------------------------------ */
    : LoginAuthenticator() {
    override fun getAuthMethod() = Constraint.__BASIC_AUTH
    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.security.Authenticator.validateRequest
     */
    override fun validateRequest(req: ServletRequest, res: ServletResponse, mandatory: Boolean): Authentication {
      val request = req as HttpServletRequest
      val response = res as HttpServletResponse
      var credentials = request.getHeader(HttpHeader.PROXY_AUTHORIZATION.asString())
      return try {
        if (!mandatory) return DeferredAuthentication(this)
        if (credentials != null) {
          val space = credentials.indexOf(' ')
          if (space > 0) {
            val method = credentials.substring(0, space)
            if ("basic".equals(method, ignoreCase = true)) {
              credentials = credentials.substring(space + 1)
              credentials = B64Code.decode(credentials, StandardCharsets.ISO_8859_1)
              val i = credentials.indexOf(':')
              if (i > 0) {
                val username = credentials.substring(0, i)
                val password = credentials.substring(i + 1)
                val user: UserIdentity? = login(username, password, request)
                if (user != null) {
                  return UserAuthentication(authMethod, user)
                }
              }
            }
          }
        }
        if (DeferredAuthentication.isDeferred(response)) return Authentication.UNAUTHENTICATED
        response.setHeader(HttpHeader.PROXY_AUTHENTICATE.asString(), "basic realm=\"" + _loginService.name + '"')
        response.sendError(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED)
        Authentication.SEND_CONTINUE
      } catch (e: IOException) {
        throw ServerAuthException(e)
      }
    }

    override fun secureResponse(req: ServletRequest?, res: ServletResponse?, mandatory: Boolean, validatedUser: Authentication.User?): Boolean {
      return true
    }
  }
}
