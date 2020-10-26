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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.UIUtil
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
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.CharsetUtil
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.exception.StartSonarLintServerException
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.SonarLintUtils
import java.net.BindException
import java.util.*
import kotlin.concurrent.thread

const val STARTING_PORT = 64120
const val INVALID_REQUEST = "Invalid request."
const val UNKNOWN_INTELLIJ_FLAVOR = "Unknown IntelliJ flavor."
const val PORT_RANGE = 3
const val ENVIRONMENT_ENDPOINT = "/sonarlint/environment"
const val OPEN_HOTSPOT = "/sonarlint/open-hotspot"
const val OK = "200 OK"
const val OPEN_IN_IDE_ERROR_TITLE = "Error during attempt to open issue in IDE"
const val PROJECT_KEY = "projectKey"
const val HOTSPOT_KEY = "hotspotKey"
const val HIDE_WARNING_PROPERTY = "SonarLint.analyzeAllFiles.hideWarning"

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
                        .childHandler(ServerInitializer())
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

class ServerInitializer : ChannelInitializer<SocketChannel?>() {
    override fun initChannel(ch: SocketChannel?) {
        ch ?: return
        val p = ch.pipeline()
        p.addLast(HttpRequestDecoder())
        p.addLast(HttpResponseEncoder())
        p.addLast(RequestHandler())
    }
}

open class RequestProcessor {

    fun processRequest(request: HttpRequest): String {
        if (request.uri() == ENVIRONMENT_ENDPOINT && request.method() == HttpMethod.GET) {
            return SonarLintUtils.getIdeVersionForTelemetry() ?: UNKNOWN_INTELLIJ_FLAVOR
        }
        if (request.uri().resolvePath() == OPEN_HOTSPOT && request.method() == HttpMethod.GET) {
            processOpenInIdeRequest(request)
            return OK
        }
        return INVALID_REQUEST
    }

    private fun processOpenInIdeRequest(request: HttpRequest) {
        val parameters = QueryStringDecoder(request.uri()).parameters()

        val projectKey = parameters[PROJECT_KEY]?.getOrNull(0) ?: run {
            showModalWindow("Project is not specified in request.")
            return
        }
        val fileName = parameters[HOTSPOT_KEY]?.getOrNull(0) ?: run {
            showModalWindow("There is no hotspot key in request.")
            return
        }

        val projectOptional = Arrays.stream(ProjectManager.getInstance().openProjects)
                .filter {
                    val projectSettings = Settings.getSettingsFor(it)
                    projectSettings.isBindingEnabled && projectSettings.projectKey == projectKey
                }.findFirst()


        if (!projectOptional.isPresent) {
            showModalWindow("Project is not opened.")
            return
        }
        val project = projectOptional.get()
        if (!isConnectedMode(project)) {
            showModalWindow("Not in connected mode.")
            return
        }
        if (project.basePath == null) {
            showModalWindow("Project path is not resolved.")
            return
        }

    }

    private fun isConnectedMode(project: Project): Boolean {
        val projectSettings = Settings.getSettingsFor(project)
        return projectSettings.isBindingEnabled
    }

    private fun String.resolvePath(): String {
        return this.substringBefore('?')
    }

    private fun showModalWindow(message: String) {
        UIUtil.invokeLaterIfNeeded {
            val result = Messages.showYesNoDialog("""
                                $message
                                """.trimIndent(),
                    OPEN_IN_IDE_ERROR_TITLE,
                    "OK", "Cancel", Messages.getWarningIcon(), DoNotShowAgain())
            // TODO react on user's choice
        }

    }

    internal class DoNotShowAgain : DoNotAskOption {
        override fun isToBeShown(): Boolean {
            return true
        }

        override fun setToBeShown(toBeShown: Boolean, exitCode: Int) {
            PropertiesComponent.getInstance().setValue(HIDE_WARNING_PROPERTY, java.lang.Boolean.toString(!toBeShown))
        }

        override fun canBeHidden(): Boolean {
            return true
        }

        override fun shouldSaveOptionsOnCancel(): Boolean {
            return false
        }

        override fun getDoNotShowMessage(): String {
            return "Don't show again"
        }
    }


}

class RequestHandler : SimpleChannelInboundHandler<HttpObject?>() {
    var request: HttpRequest? = null
    var buf = StringBuilder()

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject?) {
        val buf = StringBuilder()
        if (msg is HttpRequest) {
            val response = RequestProcessor().processRequest(msg)
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
        val service = SonarLintUtils.getService(GlobalLogOutput::class.java)
        service.logError("Error during request handling in SonarLint server", cause)
        ctx.close()
    }

}

