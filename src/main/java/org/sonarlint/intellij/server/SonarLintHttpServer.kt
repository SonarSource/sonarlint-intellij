package org.sonarlint.intellij.server

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.ui.UIUtil
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
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.multipart.InterfaceHttpData
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.util.SonarLintUtils
import java.net.BindException
import java.util.*
import kotlin.concurrent.thread

const val STARTING_PORT = 63000
const val INVALID_REQUEST = "Invalid request."
const val UNKNOWN_INTELLIJ_FLAVOR = "Unknown IntelliJ flavor."
const val PORT_RANGE = 3
const val ENVIRONMENT_ENDPOINT = "/sonarlint/environment"
const val OPEN_HOTSPOT = "/sonarlint/open-hotspot"
const val OK = "200 OK"
const val OPEN_IN_IDE_ERROR_TITLE = "Error during attempt to open issue in IDE"
const val PROJECT_KEY = "projectKey"
const val FILE_NAME = "fileName"
const val LINE_NUMBER = "lineNumber"
val LEGAL_REQUEST_PARAMETERS = listOf(
        PROJECT_KEY,
        FILE_NAME,
        LINE_NUMBER,
        "uuid",
        "componentKeys",
        "branch",
        "pullRequest",
        "commit")
val MANDATORY_REQUEST_PARAMETERS = listOf("uuid")
const val HIDE_WARNING_PROPERTY = "SonarLint.analyzeAllFiles.hideWarning"


object SonarLintHttpServer {

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
                throw RuntimeException("Couldn't start SonarLint server in port range: $STARTING_PORT - ${STARTING_PORT + PORT_RANGE}")
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
        p.addLast(HttpSnoopServerHandler())
    }
}

class HttpSnoopServerHandler : SimpleChannelInboundHandler<Any?>() {

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    private fun String.resolvePath(): String {
        return this.substringBefore('?')
    }

    private fun processRequest(request: HttpRequest): String {
        if (request.uri().resolvePath() == ENVIRONMENT_ENDPOINT && request.method() == HttpMethod.GET) {
            return SonarLintUtils.getIdeVersionForTelemetry() ?: UNKNOWN_INTELLIJ_FLAVOR
        }
        if (request.uri().resolvePath() == OPEN_HOTSPOT && request.method() == HttpMethod.GET) {
            GlobalScope.launch {
                processOpenInIdeRequest(request)
            }
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
        val fileName = parameters[FILE_NAME]?.getOrNull(0) ?: run {
            showModalWindow("There is no file in request.")
            return
        }
        val lineNumber = parameters[LINE_NUMBER]?.getOrNull(0) ?: run {
            showModalWindow("There is no line number in request.")
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

        val vFiles = ProjectRootManager.getInstance(project).contentRoots
        var virtualFiles = vFiles.mapNotNull { it.findFileByRelativePath(fileName) }
        if (virtualFiles.isEmpty()) {
            showModalWindow("No such file.")
            return
        }

        val virtualFile = virtualFiles[0]
        UIUtil.invokeLaterIfNeeded {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile != null && psiFile.isValid) {
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                // Line numbers starts from 0 in IntelliJ. And they starts form 1 in SonarQube. We need to subtract 1.
                if ((lineNumber.toInt() < 1)) {
                    showModalWindow("Invalid line number")
                }
                val lineStartOffset = document!!.getLineStartOffset(lineNumber.toInt() - 1)
                ApplicationManager.getApplication().invokeLater { OpenFileDescriptor(project, psiFile.virtualFile, lineStartOffset).navigate(true) }
            }
        }
    }

    private fun isConnectedMode(project: Project): Boolean {
        val projectSettings = Settings.getSettingsFor(project)
        return projectSettings.isBindingEnabled
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

    fun sanitizeParameters(bodyHttpDatas: List<InterfaceHttpData>): Boolean {
        MANDATORY_REQUEST_PARAMETERS.forEach { mandatoryParam ->
            if (!bodyHttpDatas.map { it.name }.contains(mandatoryParam)) return false
        }
        bodyHttpDatas.forEach {
            if (!LEGAL_REQUEST_PARAMETERS.contains(it.name)) return false
        }
        return true
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Any?) {
        val request: HttpRequest
        val buf = StringBuilder()
        if (msg is HttpRequest) {
            request = msg
            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx)
            }

            val response = processRequest(request)
            buf.setLength(0)
            buf.append(response)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        cause.printStackTrace()
        ctx.close()
    }

    companion object {
        private fun send100Continue(ctx: ChannelHandlerContext) {
            val response: FullHttpResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER)
            ctx.write(response)
        }

    }
}

