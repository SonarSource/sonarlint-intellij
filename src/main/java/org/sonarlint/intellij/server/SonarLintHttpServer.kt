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

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.CharsetUtil
import org.sonarlint.intellij.exception.StartSonarLintServerException
import org.sonarlint.intellij.util.SonarLintUtils
import java.net.BindException
import kotlin.concurrent.thread

const val STARTING_PORT = 64120
const val INVALID_REQUEST = "Invalid request."
const val UNKNOWN_INTELLIJ_FLAVOR = "Unknown IntelliJ flavor."
const val PORT_RANGE = 3
const val ENVIRONMENT_ENDPOINT = "/sonarlint/environment"

open class SonarLintHttpServer {

    fun start() {
        tryToStart(0)
    }

    fun tryToStart(numberOfAttempts: Int) {
        try {
            actualStart(STARTING_PORT + numberOfAttempts)
        } catch (e: BindException) {
            if (numberOfAttempts < PORT_RANGE) {
                tryToStart(numberOfAttempts + 1)
            } else {
                throw StartSonarLintServerException("Couldn't start SonarLint server in port range: $STARTING_PORT - ${STARTING_PORT + PORT_RANGE}")
            }
        }
    }

    fun actualStart(port: Int) {
        // don't block UI thread
        thread {
            val bossGroup: EventLoopGroup = NioEventLoopGroup(1)
            val workerGroup: EventLoopGroup = NioEventLoopGroup()
            try {
                val b = ServerBootstrap()
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel::class.java)
                        .handler(LoggingHandler(LogLevel.INFO))
                        .childHandler(HttpSnoopServerInitializer())
                val ch = b.bind(port).sync().channel()
                ch.closeFuture().sync()
            } catch (e: Exception) {
                throw e
            } finally {
                bossGroup.shutdownGracefully()
                workerGroup.shutdownGracefully()
            }
        }
    }
}

class HttpSnoopServerInitializer : ChannelInitializer<SocketChannel?>() {
    override fun initChannel(ch: SocketChannel?) {
        ch ?: return
        val p = ch.pipeline()
        p.addLast(HttpRequestDecoder())
        p.addLast(HttpResponseEncoder())
        p.addLast(SonarHttpRequestHandler())
    }
}

open class RequestProcessor {
    fun processRequest(request: HttpRequest): String {
        if (request.uri() == ENVIRONMENT_ENDPOINT && request.method() == HttpMethod.GET) {
            return SonarLintUtils.getIdeVersionForTelemetry() ?: UNKNOWN_INTELLIJ_FLAVOR
        }
        return INVALID_REQUEST
    }
}

class SonarHttpRequestHandler : SimpleChannelInboundHandler<Any?>() {
    var request: HttpRequest? = null

    var buf = StringBuilder()
    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    public override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is HttpRequest) {
            request = msg
            val request = request as HttpRequest
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx)
            }

            val response = RequestProcessor().processRequest(request)
            buf.setLength(0)
            buf.append(response)
        }
        if (msg is HttpContent && msg is LastHttpContent && !writeResponse(msg, ctx)) {

            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }

    fun writeResponse(currentObj: HttpObject, ctx: ChannelHandlerContext): Boolean {
        val keepAlive = HttpUtil.isKeepAlive(request)
        val response: FullHttpResponse = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                if (currentObj.decoderResult().isSuccess) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST,
                Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8))
        response.headers()[HttpHeaderNames.CONTENT_TYPE] = "text/plain; charset=UTF-8"
        if (keepAlive) {
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
            response.headers()[HttpHeaderNames.CONNECTION] = HttpHeaderValues.KEEP_ALIVE
        }
        ctx.write(response)
        return keepAlive
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        // TODO change to LOGGER
        cause.printStackTrace()
        ctx.close()
    }

    companion object {

        fun send100Continue(ctx: ChannelHandlerContext) {
            val response: FullHttpResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER)
            ctx.write(response)
        }

    }
}
