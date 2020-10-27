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
