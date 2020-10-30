/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.sonarlint.intellij.eq

class SonarLintHttpServerTest {

    lateinit var underTest:SonarLintHttpServer
    private lateinit var nettyServerMock: NettyServer

    @Before
    fun init() {
        nettyServerMock = mock(NettyServer::class.java)
        underTest = SonarLintHttpServer(nettyServerMock)
    }

    @Test
    fun it_should_bind_to_64120_port(){
        `when`(nettyServerMock.bindTo(64120)).thenReturn(true)

        underTest.startOnce()

        assertThat(underTest.isStarted).isTrue()
        verify(nettyServerMock).bindTo(eq(64120))
    }

    @Test
    fun it_should_bind_to_64121_port_if_64120_is_not_available(){
        `when`(nettyServerMock.bindTo(64120)).thenReturn(false)
        `when`(nettyServerMock.bindTo(64121)).thenReturn(true)

        underTest.startOnce()

        assertThat(underTest.isStarted).isTrue()
        verify(nettyServerMock).bindTo(eq(64121))
    }

    @Test
    fun it_should_try_3_consecutive_ports_and_give_up(){
        `when`(nettyServerMock.bindTo(anyInt())).thenReturn(false)

        underTest.startOnce()

        assertThat(underTest.isStarted).isFalse()
        verify(nettyServerMock).bindTo(eq(64122))
        verify(nettyServerMock, times(0)).bindTo(eq(64123))
    }

}
