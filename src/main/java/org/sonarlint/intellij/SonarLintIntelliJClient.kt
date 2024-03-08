/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.proxy.CommonProxy
import java.io.ByteArrayInputStream
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CancellationException
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.eclipse.lsp4j.jsonrpc.CancelChecker
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.sonarlint.intellij.actions.OpenInBrowserAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.vcs.ModuleVcsRepoProvider
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.AutomaticServerConnectionCreator
import org.sonarlint.intellij.config.global.wizard.ManualServerConnectionCreator
import org.sonarlint.intellij.connected.SonarProjectBranchCache
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_SETUP_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.SUPPORT_POLICY_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.TROUBLESHOOTING_CONNECTED_MODE_SETUP_LINK
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsRefreshTrigger
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilityMatcher
import org.sonarlint.intellij.notifications.OpenLinkAction
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.notifications.binding.BindingSuggestion
import org.sonarlint.intellij.progress.BackendTaskProgressReporter
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.ProjectUtils.tryFindFile
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.SonarLintAppUtils.getRelativePathForAnalysis
import org.sonarlint.intellij.util.VirtualFileUtils
import org.sonarlint.intellij.util.computeInEDT
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.event.DidReceiveServerHotspotEvent
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto


object SonarLintIntelliJClient : SonarLintRpcClientDelegate {

    private const val SONAR_SCANNER_CONFIG_FILENAME = "sonar-project.properties"
    private const val AUTOSCAN_CONFIG_FILENAME = ".sonarcloud.properties"
    private var findingToShow: ShowFinding<*>? = null
    private val backendTaskProgressReporter = BackendTaskProgressReporter()

    override fun suggestBinding(suggestionsByConfigScopeId: Map<String, List<BindingSuggestionDto>>) {
        suggestionsByConfigScopeId.forEach { (configScopeId, suggestions) -> suggestAutoBind(findProject(configScopeId), suggestions) }
    }

    private fun suggestAutoBind(project: Project?, suggestions: List<BindingSuggestionDto>) {
        if (project == null) {
            GlobalLogOutput.get().log("Discarding binding suggestions, project was closed", ClientLogOutput.Level.DEBUG)
            return
        }
        if (getSettingsFor(project).isBindingSuggestionsEnabled) {
            val notifications = SonarLintProjectNotifications.get(project)
            notifications.suggestBindingOptions(suggestions.map {
                BindingSuggestion(it.connectionId, it.sonarProjectKey, it.sonarProjectName)
            })
        }
    }

    private fun findProject(configScopeId: String): Project? {
        // XXX modules?
        return ProjectManager.getInstance().openProjects.find { configScopeId == BackendService.projectId(it) }
    }

    override fun openUrlInBrowser(url: URL) {
        BrowserUtil.browse(url)
    }

    override fun showMessage(type: MessageType, text: String) {
        SonarLintProjectNotifications.projectLessNotification(null, text, convert(type))
    }

    override fun log(params: LogParams) {
        val configScopeId = params.configScopeId

        configScopeId?.let {
            val project = BackendService.findModule(configScopeId)?.project ?: BackendService.findProject(params.configScopeId!!)
            project?.let {
                val console: SonarLintConsole = getService(project, SonarLintConsole::class.java)
                logProjectLevel(params, console)
                return
            }
        }

        val globalLogOutput = getService(GlobalLogOutput::class.java)
        globalLogOutput.log(params.message, mapLevel(params.level))
    }


    private fun mapLevel(level: LogLevel): ClientLogOutput.Level {
        return when (level) {
            LogLevel.ERROR -> {
                ClientLogOutput.Level.ERROR
            }

            LogLevel.WARN -> {
                ClientLogOutput.Level.WARN
            }

            LogLevel.INFO -> {
                ClientLogOutput.Level.INFO
            }

            LogLevel.DEBUG -> {
                ClientLogOutput.Level.DEBUG
            }

            LogLevel.TRACE -> {
                ClientLogOutput.Level.TRACE
            }
        }
    }


    override fun showSoonUnsupportedMessage(params: ShowSoonUnsupportedMessageParams) {
        val project = BackendService.findModule(params.configurationScopeId)?.project
            ?: BackendService.findProject(params.configurationScopeId) ?: return
        showOneTimeBalloon(project, params.text, params.doNotShowAgainId, OpenLinkAction(SUPPORT_POLICY_LINK, "Learn more"))
    }

    private fun logProjectLevel(
        params: LogParams,
        console: SonarLintConsole,
    ) {
        when (params.level) {
            LogLevel.TRACE, LogLevel.DEBUG -> console.debug(params.message)
            LogLevel.ERROR -> console.error(params.message)
            else -> console.info(params.message)
        }
    }

    override fun showSmartNotification(params: ShowSmartNotificationParams) {
        val projects = params.scopeIds.mapNotNull {
            BackendService.findModule(it)?.project ?: BackendService.findProject(it)
        }.toSet()
        projects.map { SonarLintProjectNotifications.get(it).handle(params) }
    }

    private fun showOneTimeBalloon(project: Project, message: String, doNotShowAgainId: String, action: AnAction?) {
        if (!PropertiesComponent.getInstance().getBoolean(doNotShowAgainId)) {
            SonarLintProjectNotifications.get(project).showOneTimeBalloon(message, doNotShowAgainId, action)
        }
    }

    override fun getClientLiveDescription(): String {
        var description = ApplicationInfo.getInstance().fullVersion
        val edition = ApplicationNamesInfo.getInstance().editionName
        if (edition != null) {
            description += " ($edition)"
        }
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isNotEmpty()) {
            description += " - " + openProjects.joinToString(", ") { it.name }
        }
        return description
    }

    private fun convert(type: MessageType): NotificationType {
        if (type == MessageType.ERROR) return NotificationType.ERROR
        if (type == MessageType.WARNING) return NotificationType.WARNING
        return NotificationType.INFORMATION
    }

    override fun showHotspot(configurationScopeId: String, hotspotDetails: HotspotDetailsDto) {
        showFinding(configurationScopeId, hotspotDetails.ideFilePath, hotspotDetails.key, hotspotDetails.rule.key, hotspotDetails.textRange, hotspotDetails.codeSnippet, LiveSecurityHotspot::class.java, emptyList(), hotspotDetails.message)
    }

    override fun showIssue(configurationScopeId: String, issueDetails: IssueDetailsDto) {
        val findingType = if (issueDetails.isTaint) LocalTaintVulnerability::class.java else LiveIssue::class.java
        showFinding(configurationScopeId, issueDetails.ideFilePath, issueDetails.issueKey, issueDetails.ruleKey, issueDetails.textRange, issueDetails.codeSnippet, findingType, issueDetails.flows, issueDetails.message)
    }

    private fun <T : Finding> showFinding(
        configScopeId: String, filePath: Path, findingKey: String, ruleKey: String,
        textRange: TextRangeDto, codeSnippet: String?, type: Class<T>, flows: List<FlowDto>, flowMessage: String,
    ) {
        val project = BackendService.findModule(configScopeId)?.project ?: BackendService.findProject(configScopeId)
        ?: throw IllegalStateException("Unable to find project with id '$configScopeId'")
        if (!project.isDisposed) {
            SonarLintProjectNotifications.get(project).expireCurrentFindingNotificationIfNeeded()
        }
        val file = tryFindFile(project, filePath)
        if (file == null) {
            if (!project.isDisposed) {
                SonarLintProjectNotifications.get(project)
                    .simpleNotification(null, "Unable to open finding. Can't find the file: $filePath", NotificationType.WARNING)
            }
            return
        }

        val module = findModuleForFile(file, project)
        if (module == null) {
            if (!project.isDisposed) {
                SonarLintProjectNotifications.get(project).simpleNotification(
                    null,
                    "Unable to open finding. Can't find the module corresponding to file: $filePath",
                    NotificationType.WARNING
                )
            }
            return
        }
        ApplicationManager.getApplication().invokeAndWait {
            openFile(project, file, textRange.startLine)
        }
        val showFinding = ShowFinding(
            module,
            ruleKey,
            findingKey,
            file,
            textRange,
            codeSnippet,
            ShowFinding.handleFlows(module.project, flows),
            flowMessage,
            type
        )
        if (getService(project, AnalysisReadinessCache::class.java).isReady) {
            getService(project, AnalysisSubmitter::class.java).analyzeFileAndTrySelectFinding(showFinding)
        } else {
            findingToShow = showFinding
        }
    }

    private fun openFile(project: Project, file: VirtualFile, line: Int) {
        OpenFileDescriptor(project, file, line - 1, -1).navigate(true)
    }

    override fun assistCreatingConnection(params: AssistCreatingConnectionParams, cancelChecker: CancelChecker): AssistCreatingConnectionResponse {
        val serverUrl = params.serverUrl
        val tokenName = params.tokenName
        val tokenValue = params.tokenValue

        val response = if (tokenName != null && tokenValue != null) {
            val newConnection = ApplicationManager.getApplication().computeInEDT {
                AutomaticServerConnectionCreator(serverUrl, tokenValue).chooseResolution()
            } ?: run {
                throw CancellationException("Connection creation cancelled by the user")
            }
            AssistCreatingConnectionResponse(newConnection.name)
        } else {
            val warningTitle = "Do you trust this SonarQube server?"
            val message = """
                        The server <b>${escapeHtml(serverUrl)}</b> is attempting to set up a connection with SonarLint. Letting SonarLint connect to an untrusted SonarQube server is potentially dangerous.
                        
                        If you don’t trust this server, we recommend canceling this action and <a href="$CONNECTED_MODE_SETUP_LINK">manually setting up Connected Mode<icon src="AllIcons.Ide.External_link_arrow" href="$CONNECTED_MODE_SETUP_LINK"></a>.
                    """.trimIndent()
            val connectButtonText = "Connect to this SonarQube server"
            val dontTrustButtonText = "I don't trust this server"

            val choice = ApplicationManager.getApplication().computeInEDT {
                MessageDialogBuilder.Message(warningTitle, message)
                    .buttons(connectButtonText, dontTrustButtonText)
                    .defaultButton(connectButtonText)
                    .focusedButton(dontTrustButtonText)
                    .asWarning()
                    .show()
            }

            if (connectButtonText != choice) {
                throw CancellationException("Connection creation rejected by the user")
            }
            val newConnection = ApplicationManager.getApplication().computeInEDT {
                ManualServerConnectionCreator().createThroughWizard(serverUrl)
            } ?: throw CancellationException("Connection creation cancelled by the user")
            AssistCreatingConnectionResponse(newConnection.name)
        }

        SonarLintProjectNotifications.projectLessNotification(
            "",
            "You have successfully established a connection to the SonarQube server",
            NotificationType.INFORMATION
        )

        return response
    }

    override fun assistBinding(params: AssistBindingParams, cancelChecker: CancelChecker): AssistBindingResponse {
        val connectionId = params.connectionId
        val projectKey = params.projectKey
        val configScopeId = params.configScopeId
        val project: Project? = if (configScopeId != null) {
            ProjectManager.getInstance().openProjects.find { BackendService.projectId(it) == configScopeId }
        } else {
            null
        }

        return if (project == null) {
            AssistBindingResponse(null)
        } else {
            val connection = getGlobalSettings().getServerConnectionByName(connectionId)
                .orElseThrow { IllegalStateException("Unable to find connection '$connectionId'") }
            getService(project, ProjectBindingManager::class.java).bindTo(connection, projectKey, emptyMap())
            SonarLintProjectNotifications.get(project).simpleNotification(
                "Project successfully bound",
                "Local project bound to project '$projectKey' of SonarQube server '${connection.name}'. " +
                    "You can now enjoy all capabilities of SonarLint Connected Mode. You can update the binding of this project in your SonarLint Settings.",
                NotificationType.INFORMATION,
                OpenInBrowserAction("Learn More in Documentation", null, CONNECTED_MODE_BENEFITS_LINK)
            )
            val module = BackendService.findModule(configScopeId)
            getService(project, SecurityHotspotsRefreshTrigger::class.java).triggerRefresh(module)
            AssistBindingResponse(BackendService.projectId(project))
        }
    }

    override fun startProgress(params: StartProgressParams) {
        backendTaskProgressReporter.startTask(params)
    }

    override fun reportProgress(params: ReportProgressParams) {
        if (params.notification.isLeft) {
            backendTaskProgressReporter.updateProgress(params.taskId, params.notification.left)
        } else {
            backendTaskProgressReporter.completeTask(params.taskId)
        }
    }

    override fun didSynchronizeConfigurationScopes(configurationScopeIds: MutableSet<String>) {
        GlobalLogOutput.get().log("Did synchronize config scopes $configurationScopeIds", ClientLogOutput.Level.INFO)
    }

    override fun getCredentials(connectionId: String): Either<TokenDto, UsernamePasswordDto> {
        val connectionOpt = getGlobalSettings().getServerConnectionByName(connectionId)
        if (connectionOpt.isEmpty) {
            throw ResponseErrorException(ResponseError(ResponseErrorCode.InvalidParams, "Unknown connection: $connectionId", connectionId))
        }
        val connection = connectionOpt.get()
        return if (connection.token != null) {
            Either.forLeft(TokenDto(connection.token))
        } else {
            Either.forRight(UsernamePasswordDto(connection.login, connection.password))
        }
    }

    override fun getProxyPasswordAuthentication(host: String, port: Int, protocol: String, prompt: String, scheme: String, targetHost: URL): GetProxyPasswordAuthenticationResponse {
        val auth = CommonProxy.getInstance().authenticator.requestPasswordAuthenticationInstance(host, null, port, protocol, prompt, scheme, targetHost, Authenticator.RequestorType.PROXY)
        return GetProxyPasswordAuthenticationResponse(auth?.userName, auth?.let { String(it.password) })
    }

    override fun checkServerTrusted(chain: List<X509CertificateDto>, authType: String): Boolean {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates: Array<X509Certificate> = chain.stream().map { certificateFactory.generateCertificate(ByteArrayInputStream(it.pem.toByteArray())) as X509Certificate }.toList().toTypedArray()
        return try {
            CertificateManager.getInstance().trustManager.checkServerTrusted(certificates, authType)
            true
        } catch (e: CertificateException) {
            GlobalLogOutput.get().logError("Certificate is not trusted", e)
            false
        }
    }

    override fun selectProxies(uri: URI): List<ProxyDto> {
        return CommonProxy.getInstance().select(uri).stream().map {
            if (it.type() != Proxy.Type.DIRECT && it.address() is InetSocketAddress) {
                val socketAddress = it.address() as InetSocketAddress
                ProxyDto(it.type(), socketAddress.hostString, socketAddress.port)
            } else {
                ProxyDto.NO_PROXY
            }
        }.toList()
    }

    override fun getTelemetryLiveAttributes(): TelemetryClientLiveAttributesResponse {
        return TelemetryClientLiveAttributesResponse(emptyMap())
    }

    override fun didReceiveServerHotspotEvent(params: DidReceiveServerHotspotEvent) {
        findProjects(params.sonarProjectKey).forEach { project ->
            val openFiles = FileEditorManager.getInstance(project).openFiles
            val filePath = params.ideFilePath
            val impactedFiles = ArrayList<VirtualFile>()

            val matchedFile = tryFindFile(project, filePath)

            if (matchedFile != null && openFiles.contains(matchedFile)) {
                impactedFiles.add(matchedFile)
            }

            getService(project, AnalysisSubmitter::class.java).autoAnalyzeFiles(impactedFiles, TriggerType.SERVER_SENT_EVENT)
        }
    }

    override fun noBindingSuggestionFound(projectKey: String) {
        SonarLintProjectNotifications.projectLessNotification(
            "No matching open project found",
            "IntelliJ can't match SonarQube project '$projectKey' to any of the currently open projects. Please open your project in IntelliJ and try again.",
            NotificationType.WARNING,
            OpenInBrowserAction("Open Troubleshooting Documentation", null, TROUBLESHOOTING_CONNECTED_MODE_SETUP_LINK)
        )
    }

    override fun didChangeAnalysisReadiness(configurationScopeIds: MutableSet<String>, areReadyForAnalysis: Boolean) {
        GlobalLogOutput.get().log("Analysis became ready=$areReadyForAnalysis for $configurationScopeIds", ClientLogOutput.Level.DEBUG)
        configurationScopeIds.mapNotNull { BackendService.findModule(it)?.project ?: findProject(it) }.toSet()
            .forEach { project ->
                getService(project, AnalysisReadinessCache::class.java).isReady = areReadyForAnalysis
                if (areReadyForAnalysis) {
                    if (findingToShow != null) {
                        getService(project, AnalysisSubmitter::class.java).analyzeFileAndTrySelectFinding(findingToShow)
                        findingToShow = null
                    }
                    // could probably be optimized by re-analyzing only the files from the ready modules, but the non-ready modules will be filtered out later
                    getService(project, AnalysisSubmitter::class.java).autoAnalyzeOpenFiles(TriggerType.BINDING_UPDATE)
                }
            }
    }

    override fun matchSonarProjectBranch(configurationScopeId: String, mainBranchName: String, allBranchesNames: Set<String>, cancelChecker: CancelChecker): String? {
        val module = BackendService.findModule(configurationScopeId) ?: return null
        val repositoriesEPs = ModuleVcsRepoProvider.EP_NAME.extensionList
        val repositories = repositoriesEPs.mapNotNull { it.getRepoFor(module) }.toList()
        if (repositories.isEmpty()) {
            return null
        }
        if (repositories.size > 1) {
            getService(module.project, SonarLintConsole::class.java).debug("Several candidate Vcs repositories detected for module $module, choosing first")
        }
        val repo = repositories.first()
        return repo.electBestMatchingServerBranchForCurrentHead(mainBranchName, allBranchesNames) ?: mainBranchName
    }

    override fun didChangeMatchedSonarProjectBranch(configScopeId: String, newMatchedBranchName: String) {
        val module = BackendService.findModule(configScopeId)
        if (module != null) {
            getService(module.project, SonarProjectBranchCache::class.java).setMatchedBranch(module, newMatchedBranchName)
        } else {
            val project = findProject(configScopeId) ?: return
            getService(project, SonarProjectBranchCache::class.java).setMatchedBranch(project, newMatchedBranchName)
        }
    }

    override fun listFiles(configScopeId: String): List<ClientFileDto> {
        return BackendService.findModule(configScopeId)?.let { module -> listModuleFiles(module, configScopeId) }
            ?: findProject(configScopeId)?.let { project -> listProjectFiles(project, configScopeId) }
            ?: emptyList()
    }

    private fun listModuleFiles(module: Module, configScopeId: String): List<ClientFileDto> {
        return listFilesInContentRoots(module).mapNotNull { file ->
            getRelativePathForAnalysis(module, file)?.let { relativePath ->
                toClientFileDto(
                    module.project,
                    configScopeId,
                    file,
                    relativePath
                )
            }
        }
    }

    private fun listProjectFiles(project: Project, configScopeId: String): List<ClientFileDto> {
        return listFilesInProjectBaseDir(project).mapNotNull { file ->
            getRelativePathForAnalysis(project, file)?.let { relativePath -> toClientFileDto(project, configScopeId, file, relativePath) }
        }
    }

    private fun toClientFileDto(project: Project, configScopeId: String, file: VirtualFile, relativePath: String): ClientFileDto? {
        if (!file.isValid || FileUtilRt.isTooLarge(file.length)) return null
        val uri = VirtualFileUtils.toURI(file) ?: return null
        var fileContent: String? = null
        if (file.name == SONAR_SCANNER_CONFIG_FILENAME || file.name == AUTOSCAN_CONFIG_FILENAME) {
            fileContent = computeReadActionSafely(project) { getFileContent(file) }
        }
        return ClientFileDto(
            uri,
            Paths.get(relativePath),
            configScopeId,
            computeReadActionSafely(project) { TestSourcesFilter.isTestSources(file, project) },
            file.charset.name(),
            Paths.get(file.path),
            fileContent
        )
    }

    private fun listFilesInContentRoots(
        module: Module,
    ): Set<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        ModuleRootManager.getInstance(module).fileIndex.iterateContent { file ->
            if (module.isDisposed) {
                return@iterateContent false;
            }

            if (!file.isDirectory && file.isValid) files.add(file)

            true
        }
        return files.toSet()
    }

    // useful for Rider where the files to find are not located in content roots
    private fun listFilesInProjectBaseDir(project: Project): Set<VirtualFile> {
        return project.guessProjectDir()?.children?.filter { !it.isDirectory && it.isValid }?.toSet() ?: return emptySet()
    }

    private fun getFileContent(virtualFile: VirtualFile): String {
        val fileDocumentManager = FileDocumentManager.getInstance()
        if (fileDocumentManager.isFileModified(virtualFile)) {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                return document.text
            }
        }
        return virtualFile.contentsToByteArray().toString(virtualFile.charset)
    }

    override fun didChangeTaintVulnerabilities(
        configurationScopeId: String, closedTaintVulnerabilityIds: Set<UUID>, addedTaintVulnerabilities: List<TaintVulnerabilityDto>,
        updatedTaintVulnerabilities: List<TaintVulnerabilityDto>,
    ) {
        val project = findProject(configurationScopeId) ?: return
        val taintVulnerabilityMatcher = TaintVulnerabilityMatcher(project)
        val (locallyMatchedAddedTaintVulnerabilities, locallyMatchedUpdatedTaintVulnerabilities) = computeReadActionSafely(project) {
            addedTaintVulnerabilities.map { taintVulnerabilityMatcher.match(it) } to updatedTaintVulnerabilities.map { taintVulnerabilityMatcher.match(it) }
        } ?: return
        getService(project, SonarLintToolWindow::class.java).updateTaintVulnerabilities(closedTaintVulnerabilityIds, locallyMatchedAddedTaintVulnerabilities, locallyMatchedUpdatedTaintVulnerabilities)
    }

    private fun findProjects(projectKey: String?) = ProjectManager.getInstance().openProjects.filter { project ->
        getService(project, ProjectBindingManager::class.java).uniqueProjectKeys.contains(projectKey)
    }.toSet()
}
