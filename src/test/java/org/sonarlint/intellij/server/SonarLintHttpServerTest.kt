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
package org.sonarlint.intellij.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.eq

class SonarLintHttpServerTest : AbstractSonarLintLightTests() {

    lateinit var underTest: SonarLintHttpServer
    private lateinit var serverImplMock: ServerImpl

    @Before
    fun prepare() {
        serverImplMock = mock(ServerImpl::class.java)
        underTest = SonarLintHttpServer(serverImplMock)
    }

    @After
    fun cleanup() {
        Settings.getGlobalSettings().serverConnections = emptyList()
    }

    @Test
    fun it_should_bind_to_64120_port() {
        `when`(serverImplMock.bindTo(64120)).thenReturn(true)

        underTest.startOnce()

        assertThat(underTest.isStarted).isTrue
        verify(serverImplMock).bindTo(eq(64120))
    }

    @Test
    fun it_should_bind_to_64121_port_if_64120_is_not_available() {
        `when`(serverImplMock.bindTo(64120)).thenReturn(false)
        `when`(serverImplMock.bindTo(64121)).thenReturn(true)

        underTest.startOnce()

        assertThat(underTest.isStarted).isTrue
        verify(serverImplMock).bindTo(eq(64121))
    }

    @Test
    fun it_should_try_consecutive_ports_and_give_up_after_64130() {
        `when`(serverImplMock.bindTo(anyInt())).thenReturn(false)

        underTest.startOnce()

        assertThat(underTest.isStarted).isFalse
        verify(serverImplMock).bindTo(eq(64130))
        verify(serverImplMock, times(0)).bindTo(eq(64131))
    }

    @Test
    fun trusted_origin_test() {
        Settings.getGlobalSettings().addServerConnection(ServerConnection.newBuilder().setHostUrl("https://my.sonar.com/sonar").build())
        assertThat(RequestHandler.isTrustedOrigin("http://foo")).isFalse
        assertThat(RequestHandler.isTrustedOrigin("https://sonarcloud.io")).isTrue
        assertThat(RequestHandler.isTrustedOrigin("https://my.sonar.com")).isTrue
        assertThat(RequestHandler.isTrustedOrigin("http://my.sonar.com")).isFalse
        assertThat(RequestHandler.isTrustedOrigin("https://sonar.com")).isFalse
    }

}
