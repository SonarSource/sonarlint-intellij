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


import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.sonarlint.intellij.util.SonarLintUtils
import kotlin.test.assertTrue



class SonarHttpRequestHandlerTest {

    private val underTest = SonarHttpRequestHandler()

    @Mock
    lateinit var request: HttpRequest

    @Mock
    lateinit var httpObject: HttpObject

    @Mock
    lateinit var ctx: ChannelHandlerContext

    @Before
    fun init() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun processRequestTest() {
        `when`(request.uri()).thenReturn(ENVIRONMENT_ENDPOINT)
        `when`(request.method()).thenReturn(HttpMethod.GET)
        val processResult = RequestProcessor().processRequest(request)

        assertThat(processResult).isEqualTo(SonarLintUtils.getIdeVersionForTelemetry() ?: UNKNOWN_INTELLIJ_FLAVOR)
    }

    @Test
    fun exceptionCaughtTest() {
        val throwableMock = mock(Throwable().javaClass)
        underTest.exceptionCaught(ctx, throwableMock)

        verify(ctx).close()
    }

    @Test
    fun send100ContinueTest() {
        val response: FullHttpResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER)
        SonarHttpRequestHandler.send100Continue(ctx)

        verify(ctx).write(response)
    }

    @Test
    fun writeResponseTest() {
        `when`(request.headers()).thenReturn(HttpHeaders.EMPTY_HEADERS)
        `when`(request.protocolVersion()).thenReturn(HttpVersion.HTTP_1_1)
        `when`(httpObject.decoderResult()).thenReturn(DecoderResult.SUCCESS)
        underTest.request = request

        assertTrue(underTest.writeResponse(httpObject, ctx))
        verify(ctx).write(any())
    }

    @Test
    fun channelReadTest() {
        val mock = mock(RequestProcessor().javaClass)
        `when`(request.headers()).thenReturn(HttpHeaders.EMPTY_HEADERS)
        `when`(request.protocolVersion()).thenReturn(HttpVersion.HTTP_1_1)

        underTest.channelRead0(ctx, request)

        verify(mock).processRequest(request)
    }

}
