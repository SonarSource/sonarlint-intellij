package org.sonarlint.intellij.server

import com.intellij.openapi.Disposable
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
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.SonarLintUtils.getService
import java.net.BindException

const val STARTING_PORT = 64120
const val PORT_RANGE = 3

/**
 * TODO Replace @Deprecated with @NonInjectable when switching to 2019.3 API level
 * @deprecated in 4.2 to silence a check in 2019.3
 */
class SonarLintHttpServer @java.lang.Deprecated constructor(private var nettyServer: NettyServer) : Disposable {

    constructor() : this(NettyServer())

    var isStarted = false

    fun startOnce() {
        var numberOfAttempts = 0
        while (!isStarted && numberOfAttempts < PORT_RANGE) {
            isStarted = nettyServer.bindTo(STARTING_PORT + numberOfAttempts)
            numberOfAttempts++
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
            b.bind(port).sync().channel()
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

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is HttpRequest) {
            response = RequestProcessor().processRequest(Request(msg.uri(), msg.method()))
        }
        if (msg is LastHttpContent) {
            ctx.writeAndFlush(createResponse(response))
        }
    }

    private fun createResponse(res: Response?): FullHttpResponse {
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
        response.headers()[HttpHeaderNames.CONTENT_TYPE] = "text/plain; charset=UTF-8"
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
        return response
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        getService(GlobalLogOutput::class.java).logError("Error processing request", cause)
        ctx.close()
    }

}
