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
package org.sonarlint.intellij.server

import com.intellij.openapi.Disposable
import com.intellij.serviceContainer.NonInjectable
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.CharsetUtil
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput
import java.net.BindException
import java.net.InetAddress

const val STARTING_PORT = 64120
const val ENDING_PORT = 64130

class SonarLintHttpServer @NonInjectable constructor(private var nettyServer: NettyServer) : Disposable {

    constructor() : this(NettyServer())

    var isStarted = false

    fun startOnce() {
        var currentPort = STARTING_PORT
        while (!isStarted && currentPort <= ENDING_PORT) {
            isStarted = nettyServer.bindTo(currentPort)
            displayStartStatus(currentPort)
            currentPort++
        }
    }

    private fun displayStartStatus(port: Int) {
        if (isStarted) {
            GlobalLogOutput.get().log("Server started on $port", ClientLogOutput.Level.INFO)
        } else {
            GlobalLogOutput.get().log("Cannot start the SonarLint server on $port", ClientLogOutput.Level.ERROR)
        }
    }

    override fun dispose() {
        nettyServer.stop()
    }

}

open class NettyServer {
    private lateinit var bossGroup: EventLoopGroup
    private lateinit var workerGroup: EventLoopGroup

    open fun bindTo(port: Int): Boolean {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()
        val b = ServerBootstrap()
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .handler(LoggingHandler(LogLevel.INFO))
            .childHandler(ServerInitializer())
        try {
            b.bind(InetAddress.getLoopbackAddress(), port).sync().channel()
        } catch (e: BindException) {
            return false
        }
        return true
    }

    fun stop() {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
    }
}

class ServerInitializer : ChannelInitializer<SocketChannel?>() {

    override fun initChannel(ch: SocketChannel?) {
        ch ?: return
        ch.pipeline().addLast(HttpRequestDecoder(), HttpResponseEncoder(), ServerHandler())
    }

}

class ServerHandler : SimpleChannelInboundHandler<Any?>() {

    private var response: Response? = null
    private var origin: String? = null

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is HttpRequest) {
            origin = msg.headers()[HttpHeaderNames.ORIGIN]
            response = RequestProcessor().processRequest(Request(msg.uri(), msg.method(), isTrustedOrigin(origin)))
        }
        if (msg is LastHttpContent) {
            ctx.writeAndFlush(createResponse(response, origin))
        }
    }

    private fun createResponse(res: Response?, origin: String?): FullHttpResponse {
        val response: FullHttpResponse = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            when (res) {
                is BadRequest -> HttpResponseStatus.BAD_REQUEST
                else -> HttpResponseStatus.OK
            },
            Unpooled.copiedBuffer(when (res) {
                is Success -> res.body ?: ""
                is BadRequest -> res.message
                else -> ""
            }, CharsetUtil.UTF_8))
        response.headers()[HttpHeaderNames.CONTENT_TYPE] = "application/json; charset=UTF-8"
        origin?.let { response.headers()[HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN] = origin }
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
        return response
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        getService(GlobalLogOutput::class.java).logError("Error processing request", cause)
        ctx.close()
    }

    companion object {
        fun isTrustedOrigin(origin: String?): Boolean {
            return origin != null && (SonarLintUtils.isSonarCloudAlias(origin) || Settings.getGlobalSettings().serverConnections.any { it.hostUrl.startsWith(origin) })
        }
    }

}
