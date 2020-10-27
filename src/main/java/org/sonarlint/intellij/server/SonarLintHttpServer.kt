package org.sonarlint.intellij.server

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.UIUtil
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.CharsetUtil
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.core.SecurityHotspotMatcher
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.issue.hotspot.SecurityHotspotOpener
import org.sonarlint.intellij.util.SonarLintUtils
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration
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


object SonarLintHttpServer {

    fun start() {
        tryToStart(0)
    }

    fun tryToStart(numberOfAttempts: Int) {
        // don't block UI thread
        thread {
            try {
                actualStart(STARTING_PORT + numberOfAttempts)
            } catch (e: BindException) {
                if (numberOfAttempts < PORT_RANGE) {
                    tryToStart(numberOfAttempts + 1)
                } else {
                    throw RuntimeException("Couldn't start SonarLint server in port range: $STARTING_PORT - ${STARTING_PORT + PORT_RANGE}")
                }
            }
        }
    }

    fun actualStart(port: Int) {
        val bossGroup: EventLoopGroup = NioEventLoopGroup(1)
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        try {
            val b = ServerBootstrap()
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .handler(LoggingHandler(LogLevel.INFO))
                    .childHandler(ServerInitializer())
            val ch = b.bind(port).sync().channel()
            System.err.println("Open your web browser and navigate to http://localhost:$port/")
            ch.closeFuture().sync()
            System.err.println("After close future")
        } catch (e: Exception) {
            System.err.println("Actual start error: ${e.message}")
            throw e
        } finally {
            System.err.println("Finally after server start.")
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }
}

class ServerInitializer : ChannelInitializer<SocketChannel?>() {
    override fun initChannel(ch: SocketChannel?) {
        ch ?: return
        val p = ch.pipeline()
        p.addLast(HttpRequestDecoder())
        p.addLast(HttpResponseEncoder())
        p.addLast(ServerHandler())
    }
}

class RequestProcessor() {

    private fun String.resolvePath(): String {
        return this.substringBefore('?')
    }

    fun processRequest(request: HttpRequest): String {
        if (request.uri().resolvePath() == ENVIRONMENT_ENDPOINT && request.method() == HttpMethod.GET) {
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
        val hotspotKey = parameters[HOTSPOT_KEY]?.getOrNull(0) ?: run {
            showModalWindow("Hotspot key is not specified in request.")
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

        ApplicationManager.getApplication().invokeLater { openHotspot(project, hotspotKey) }

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

    private fun openHotspot(project: Project, hotspotId: String) {
        try {
            SecurityHotspotOpener(getConnectedServerConfig(project), SecurityHotspotMatcher(project))
                    .open(project, hotspotId, Settings.getSettingsFor(project).projectKey)
        } catch (invalidBindingException: InvalidBindingException) {
            Messages.showErrorDialog("The project binding is invalid", "Error Fetching Security Hotspot")
        }
    }

    @Throws(InvalidBindingException::class)
    private fun getConnectedServerConfig(project: Project): ServerConfiguration? {
        val bindingManager = SonarLintUtils.getService(project, ProjectBindingManager::class.java)
        val server = bindingManager.sonarQubeServer
        return SonarLintUtils.getServerConfiguration(server)
    }
}


class ServerHandler : SimpleChannelInboundHandler<Any?>() {

    private var request: HttpRequest? = null

    private val buf = StringBuilder()

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is HttpRequest) {
            request = msg
            val response = RequestProcessor().processRequest(msg)
            buf.setLength(0)
            buf.append(response)
        }
        if (msg is LastHttpContent) {
            writeResponse(msg, ctx)
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }

    private fun writeResponse(currentObj: HttpObject, ctx: ChannelHandlerContext): Boolean {
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
        cause.printStackTrace()
        ctx.close()
    }

}

