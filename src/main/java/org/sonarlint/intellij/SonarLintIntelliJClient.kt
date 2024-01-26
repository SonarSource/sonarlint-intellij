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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.proxy.CommonProxy
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.sonarlint.intellij.actions.OpenInBrowserAction
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.AutomaticServerConnectionCreator
import org.sonarlint.intellij.config.global.wizard.ManualServerConnectionCreator
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_SETUP_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.SUPPORT_POLICY_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.TROUBLESHOOTING_CONNECTED_MODE_SETUP_LINK
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesPresenter
import org.sonarlint.intellij.notifications.OpenLinkAction
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.notifications.binding.BindingSuggestion
import org.sonarlint.intellij.progress.BackendTaskProgressReporter
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.ProjectUtils.tryFindFile
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.computeInEDT
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse
import org.sonarsource.sonarlint.core.clientapi.client.binding.NoBindingSuggestionFoundParams
import org.sonarsource.sonarlint.core.clientapi.client.binding.SuggestBindingParams
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsParams
import org.sonarsource.sonarlint.core.clientapi.client.connection.GetCredentialsResponse
import org.sonarsource.sonarlint.core.clientapi.client.event.DidReceiveServerEventParams
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse
import org.sonarsource.sonarlint.core.clientapi.client.fs.FoundFileDto
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedParams
import org.sonarsource.sonarlint.core.clientapi.client.http.CheckServerTrustedResponse
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationParams
import org.sonarsource.sonarlint.core.clientapi.client.http.GetProxyPasswordAuthenticationResponse
import org.sonarsource.sonarlint.core.clientapi.client.http.ProxyDto
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesParams
import org.sonarsource.sonarlint.core.clientapi.client.http.SelectProxiesResponse
import org.sonarsource.sonarlint.core.clientapi.client.info.GetClientInfoResponse
import org.sonarsource.sonarlint.core.clientapi.client.issue.ShowIssueParams
import org.sonarsource.sonarlint.core.clientapi.client.message.MessageType
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowSoonUnsupportedMessageParams
import org.sonarsource.sonarlint.core.clientapi.client.progress.ReportProgressParams
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams
import org.sonarsource.sonarlint.core.clientapi.client.smartnotification.ShowSmartNotificationParams
import org.sonarsource.sonarlint.core.clientapi.client.sync.DidSynchronizeConfigurationScopeParams
import org.sonarsource.sonarlint.core.clientapi.common.FlowDto
import org.sonarsource.sonarlint.core.clientapi.common.TextRangeDto
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger
import org.sonarsource.sonarlint.core.commons.push.ServerEvent
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotChangedEvent
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotClosedEvent
import org.sonarsource.sonarlint.core.serverapi.push.SecurityHotspotRaisedEvent
import org.sonarsource.sonarlint.core.serverapi.push.ServerHotspotEvent
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent
import java.io.ByteArrayInputStream
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

object SonarLintIntelliJClient : SonarLintClient {

    private val backendTaskProgressReporter = BackendTaskProgressReporter()

    override fun suggestBinding(params: SuggestBindingParams) {
        params.suggestions.forEach { (projectId, suggestions) -> suggestAutoBind(findProject(projectId), suggestions) }
    }

    private fun suggestAutoBind(project: Project?, suggestions: List<BindingSuggestionDto>) {
        if (project == null) {
            GlobalLogOutput.get().log("Discarding binding suggestions, project was closed", ClientLogOutput.Level.DEBUG)
            return
        }
        if (getSettingsFor(project).isBindingSuggestionsEnabled) {
            val notifications = SonarLintProjectNotifications.get(project)
            notifications.suggestBindingOptions(suggestions.map {
                BindingSuggestion(
                    it.connectionId, it.sonarProjectKey, it.sonarProjectName
                )
            })
        }
    }

    override fun findFileByNamesInScope(params: FindFileByNamesInScopeParams): CompletableFuture<FindFileByNamesInScopeResponse> {
        val project = findProject(params.configScopeId) ?: return CompletableFuture.completedFuture(
            FindFileByNamesInScopeResponse(emptyList())
        )

        val foundFiles = computeReadActionSafely(project) {
            findFiles(project, params)
        }

        return CompletableFuture.completedFuture(FindFileByNamesInScopeResponse(foundFiles ?: emptyList()))
    }

    private fun findFiles(project: Project, params: FindFileByNamesInScopeParams): List<FoundFileDto> {
        if (project.isDisposed) {
            return emptyList()
        }
        val fileNames = params.filenames
        val foundVirtualFiles = findInContentRoots(project, fileNames) + findInProjectBaseDir(project, fileNames)
        return foundVirtualFiles.map {
            FoundFileDto(
                it.name, it.path, getFileContent(it)
            )
        }
    }

    private fun findInContentRoots(
        project: Project, fileNames: List<String>,
    ): Set<VirtualFile> {
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots.filter { it.isDirectory }
        return fileNames.mapNotNull { fileName ->
            contentRoots.firstNotNullOfOrNull { root ->
                root.findFileByRelativePath(
                    fileName
                )
            }
        }.toSet()
    }

    // useful for Rider where the files to find are not located in content roots
    private fun findInProjectBaseDir(project: Project, fileNames: List<String>): Set<VirtualFile> {
        val projectDir = project.guessProjectDir() ?: return emptySet()
        return fileNames.mapNotNull { fileName ->
            projectDir.findFileByRelativePath(
                fileName
            )
        }.toSet()
    }

    private fun findProject(configScopeId: String): Project? {
        // XXX modules?
        return ProjectManager.getInstance().openProjects.find { configScopeId == BackendService.projectId(it) }
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

    override fun openUrlInBrowser(params: OpenUrlInBrowserParams) {
        BrowserUtil.browse(params.url)
    }

    override fun showMessage(params: ShowMessageParams) {
        SonarLintProjectNotifications.projectLessNotification(null, params.text, convert(params.type))
    }

    override fun showSoonUnsupportedMessage(params: ShowSoonUnsupportedMessageParams) {
        val project = BackendService.findModule(params.configurationScopeId)?.project ?: BackendService.findProject(params.configurationScopeId) ?: return
        showOneTimeBalloon(project, params.text, params.doNotShowAgainId, OpenLinkAction(SUPPORT_POLICY_LINK, "Learn more"))
    }

    override fun showSmartNotification(params: ShowSmartNotificationParams) {
        val projects = params.scopeIds.mapNotNull { BackendService.findModule(it)?.project ?: BackendService.findProject(it) }.toSet()
        projects.map { SonarLintProjectNotifications.get(it).handle(params) }
    }

    private fun convert(type: String?): NotificationType {
        if (type == MessageType.ERROR.name) return NotificationType.ERROR
        if (type == MessageType.WARNING.name) return NotificationType.WARNING
        return NotificationType.INFORMATION
    }

    private fun showOneTimeBalloon(project: Project, message: String, doNotShowAgainId: String, action: AnAction?) {
        if (!PropertiesComponent.getInstance().getBoolean(doNotShowAgainId)) {
            SonarLintProjectNotifications.get(project).showOneTimeBalloon(message, doNotShowAgainId, action)
        }
    }

    override fun getClientInfo(): CompletableFuture<GetClientInfoResponse> {
        var description = ApplicationInfo.getInstance().fullVersion
        val edition = ApplicationNamesInfo.getInstance().editionName
        if (edition != null) {
            description += " ($edition)"
        }
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isNotEmpty()) {
            description += " - " + openProjects.joinToString(", ") { it.name }
        }
        val hostInfoDto = GetClientInfoResponse(description)
        return CompletableFuture.completedFuture(hostInfoDto)
    }

    override fun showHotspot(params: ShowHotspotParams) {
        showFinding(params.configurationScopeId, params.hotspotDetails.filePath, params.hotspotDetails.key,
            params.hotspotDetails.rule.key, params.hotspotDetails.textRange, params.hotspotDetails.codeSnippet, LiveSecurityHotspot::class.java, emptyList(), params.hotspotDetails.message
        )
    }

    override fun showIssue(params: ShowIssueParams) {
        val findingType = if (params.isTaint) LocalTaintVulnerability::class.java else LiveIssue::class.java
        showFinding(params.configScopeId, params.serverRelativeFilePath,
                params.issueKey, params.ruleKey, params.textRange, params.codeSnippet, findingType, params.flows, params.message)
    }

    private fun <T: Finding> showFinding(configScopeId: String, filePath: String, findingKey: String, ruleKey: String,
        textRange: TextRangeDto, codeSnippet: String?, type: Class<T>, flows: List<FlowDto>, flowMessage: String) {
        val project = findProject(configScopeId) ?: throw IllegalStateException("Unable to find project with id '$configScopeId'")
        SonarLintProjectNotifications.get(project).expireCurrentFindingNotificationIfNeeded()
        val file = tryFindFile(project, filePath)
        if (file == null) {
            SonarLintProjectNotifications.get(project).simpleNotification("Unable to open finding. Can't find the file: $filePath", NotificationType.WARNING)
            return
        }

        val module = findModuleForFile(file, project)
        if (module == null) {
            SonarLintProjectNotifications.get(project).simpleNotification("Unable to open finding. Can't find the module corresponding to file: $filePath", NotificationType.WARNING)
            return
        }
        ApplicationManager.getApplication().invokeAndWait {
            openFile(project, file, textRange.startLine)
        }
        val showFinding = ShowFinding(module, ruleKey, findingKey, file, textRange, codeSnippet, ShowFinding.handleFlows(module.project, flows), flowMessage, type)
        getService(project, AnalysisSubmitter::class.java).analyzeFileAndTrySelectFinding(showFinding)
    }

    private fun openFile(project: Project, file: VirtualFile, line: Int) {
        OpenFileDescriptor(project, file, line - 1, -1).navigate(true)
    }

    override fun assistCreatingConnection(params: AssistCreatingConnectionParams): CompletableFuture<AssistCreatingConnectionResponse> {
        return CompletableFuture.supplyAsync {
            val serverUrl = params.serverUrl
            val tokenName = params.tokenName
            val tokenValue = params.tokenValue
            val currentConfigScopeIds = ProjectManager.getInstance().openProjects.map { project -> BackendService.projectId(project) }.toSet()

            val response = if (tokenName != null && tokenValue != null) {
                val newConnection = ApplicationManager.getApplication().computeInEDT {
                    AutomaticServerConnectionCreator(serverUrl, tokenValue).chooseResolution()
                } ?: run {
                    throw CancellationException("Connection creation cancelled by the user")
                }
                AssistCreatingConnectionResponse(newConnection.name, currentConfigScopeIds)
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
                AssistCreatingConnectionResponse(newConnection.name, currentConfigScopeIds)
            }

            SonarLintProjectNotifications.projectLessNotification(
                "",
                "You have successfully established a connection to the SonarQube server",
                NotificationType.INFORMATION)

            response
        }
    }

    override fun assistBinding(params: AssistBindingParams): CompletableFuture<AssistBindingResponse> {
        return CompletableFuture.supplyAsync {
            val connectionId = params.connectionId
            val projectKey = params.projectKey
            val configScopeId = params.configScopeId
            val project: Project? = if (configScopeId != null) {
                ProjectManager.getInstance().openProjects.find { BackendService.projectId(it) == configScopeId }
            } else {
                null
            }

            if (project == null) {
                AssistBindingResponse(null)
            } else {
                val connection = getGlobalSettings().getServerConnectionByName(connectionId)
                        .orElseThrow { IllegalStateException("Unable to find connection '$connectionId'") }

                getService(project, ProjectBindingManager::class.java).bindTo(connection, projectKey, emptyMap())
                SonarLintProjectNotifications.projectLessNotification(
                    "Project successfully bound",
                    "Local project bound to project '$projectKey' of SonarQube server '${connection.name}'. " +
                        "You can now enjoy all capabilities of SonarLint Connected Mode. You can update the binding of this project in your SonarLint Settings.",
                    NotificationType.INFORMATION,
                    OpenInBrowserAction("Learn More in Documentation", null, CONNECTED_MODE_BENEFITS_LINK))
                AssistBindingResponse(BackendService.projectId(project))
            }
        }
    }

    override fun startProgress(params: StartProgressParams): CompletableFuture<Void> {
        return backendTaskProgressReporter.startTask(params)
    }

    override fun reportProgress(params: ReportProgressParams) {
        if (params.notification.isLeft) {
            backendTaskProgressReporter.updateProgress(params.taskId, params.notification.left)
        } else {
            backendTaskProgressReporter.completeTask(params.taskId)
        }
    }

    override fun didSynchronizeConfigurationScopes(params: DidSynchronizeConfigurationScopeParams) {
        if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
            params.configurationScopeIds.mapNotNull { scopeId ->
                    BackendService.findModule(scopeId)?.project ?: BackendService.findProject(scopeId)
            }
                .toSet()
                .forEach { project ->
                    getService(
                        project,
                        TaintVulnerabilitiesPresenter::class.java
                    ).presentTaintVulnerabilitiesForOpenFiles()
                }
        }
    }

    override fun getCredentials(params: GetCredentialsParams): CompletableFuture<GetCredentialsResponse> {
        return getGlobalSettings().getServerConnectionByName(params.connectionId)
            .map { connection -> connection.token?.let { CompletableFuture.completedFuture(GetCredentialsResponse(TokenDto(it))) }
                ?: connection.login?.let { CompletableFuture.completedFuture(GetCredentialsResponse(UsernamePasswordDto(it, connection.password))) }
                ?: CompletableFuture.failedFuture(IllegalArgumentException("Invalid credentials for connection: " + params.connectionId))
            }.orElse(CompletableFuture.failedFuture(IllegalArgumentException("Unknown connection: " + params.connectionId)))
    }

    override fun getProxyPasswordAuthentication(params: GetProxyPasswordAuthenticationParams): CompletableFuture<GetProxyPasswordAuthenticationResponse> {
        val targetHostUrl: URL
        try {
            targetHostUrl = URL(params.targetHostURL)
        } catch (e: MalformedURLException) {
            return CompletableFuture.failedFuture(e)
        }
        val auth = CommonProxy.getInstance().authenticator.requestPasswordAuthenticationInstance(
            params.host,
            null,
            params.port,
            params.protocol,
            params.prompt,
            params.scheme,
            targetHostUrl,
            Authenticator.RequestorType.PROXY
        )
        return CompletableFuture.completedFuture(GetProxyPasswordAuthenticationResponse(auth?.userName, auth?.let { String(it.password) }))
    }

    override fun checkServerTrusted(params: CheckServerTrustedParams): CompletableFuture<CheckServerTrustedResponse> {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates: Array<X509Certificate> = params.chain.stream()
            .map { certificateFactory.generateCertificate(ByteArrayInputStream(it.pem.toByteArray())) as X509Certificate }
            .toList().toTypedArray()
        return try {
            CertificateManager.getInstance().trustManager.checkServerTrusted(certificates, params.authType)
            CompletableFuture.completedFuture(CheckServerTrustedResponse(true))
        } catch (e: CertificateException) {
            SonarLintLogger.get().warn("Certificate is not trusted", e.message)
            return CompletableFuture.completedFuture(CheckServerTrustedResponse(false))
        }
    }

    override fun selectProxies(params: SelectProxiesParams): CompletableFuture<SelectProxiesResponse> {
        val uri = URI.create(params.uri)
        val proxiesResponse =
            SelectProxiesResponse(CommonProxy.getInstance().select(uri).stream().map {
                if (it.type() != Proxy.Type.DIRECT && it.address() is InetSocketAddress) {
                    val socketAddress = it.address() as InetSocketAddress
                    ProxyDto(it.type(), socketAddress.hostString, socketAddress.port)
                } else {
                    ProxyDto.NO_PROXY
                }
            }.toList())
        return CompletableFuture.completedFuture(proxiesResponse)
    }

    override fun didReceiveServerEvent(params: DidReceiveServerEventParams) {
        val event = params.serverEvent
        identifyProjectsImpactedByTaintEvent(event).forEach { project ->
            getService(
                project, TaintVulnerabilitiesPresenter::class.java
            ).presentTaintVulnerabilitiesForOpenFiles()
        }

        identifyProjectsImpactedBySecurityHotspotEvent(event).forEach { project ->
            val openFiles = FileEditorManager.getInstance(project).openFiles
            val filePath = (event as ServerHotspotEvent).filePath
            val impactedFiles = ArrayList<VirtualFile>()

            ProjectRootManager.getInstance(project).contentRoots.forEach {
                if (it.isDirectory) {
                    val matchedFile = it.findFileByRelativePath(filePath)
                    if (matchedFile != null && openFiles.contains(matchedFile)) {
                        impactedFiles.add(matchedFile)
                    }
                } else {
                    if (it.path.endsWith(filePath) && openFiles.contains(it)) {
                        impactedFiles.add(it)
                    }
                }
            }
            getService(project, AnalysisSubmitter::class.java).autoAnalyzeFiles(impactedFiles, TriggerType.SERVER_SENT_EVENT)
        }
    }

    override fun noBindingSuggestionFound(params: NoBindingSuggestionFoundParams) {
        SonarLintProjectNotifications.projectLessNotification(
            "No matching open project found",
            "IntelliJ can't match SonarQube project '${params.projectKey}' to any of the currently open projects. Please open your project in IntelliJ and try again.",
            NotificationType.WARNING,
            OpenInBrowserAction("Open Troubleshooting Documentation", null, TROUBLESHOOTING_CONNECTED_MODE_SETUP_LINK))
    }

    private fun identifyProjectsImpactedByTaintEvent(event: ServerEvent): Set<Project> {
        val projectKey = when (event) {
            is TaintVulnerabilityRaisedEvent -> event.projectKey
            is TaintVulnerabilityClosedEvent -> event.projectKey
            is IssueChangedEvent -> event.projectKey
            else -> null
        }
        return ProjectManager.getInstance().openProjects.filter { project ->
            getService(
                project, ProjectBindingManager::class.java
            ).uniqueProjectKeys.contains(projectKey)
        }.toSet()
    }

    private fun identifyProjectsImpactedBySecurityHotspotEvent(event: ServerEvent): Set<Project> {
        val projectKey = when (event) {
            is SecurityHotspotChangedEvent -> event.projectKey
            is SecurityHotspotClosedEvent -> event.projectKey
            is SecurityHotspotRaisedEvent -> event.projectKey
            else -> null
        }

        return ProjectManager.getInstance().openProjects.filter { project ->
            getService(
                project, ProjectBindingManager::class.java
            ).uniqueProjectKeys.contains(projectKey)
        }.toSet()
    }
}
