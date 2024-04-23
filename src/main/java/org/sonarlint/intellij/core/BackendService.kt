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
package org.sonarlint.intellij.core

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.jcef.JBCefApp
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Filter
import java.util.logging.Level
import java.util.logging.Logger
import org.apache.commons.io.FileUtils
import org.sonarlint.intellij.SonarLintIntelliJClient
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.actions.RestartBackendAction.Companion.SONARLINT_ERROR_MSG
import org.sonarlint.intellij.actions.RestartBackendNotificationAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.NodeJsSettings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilityMatcher
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.projectLessNotification
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.ProjectUtils.getRelativePaths
import org.sonarlint.intellij.util.VirtualFileUtils
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.core.client.utils.IssueResolutionStatus
import org.sonarsource.sonarlint.core.rpc.client.Sloop
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetFilesStatusParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.FeatureFlagsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SslConfigurationDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ListAllStandaloneRulesDefinitionsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams as issueCheckStatusChangePermittedParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedResponse as issueCheckStatusChangePermittedResponse

@Service(Service.Level.APP)
class BackendService : Disposable {
    private var initializationTriedOnce = AtomicBoolean(false)
    private var backendFuture = CompletableFuture<SonarLintRpcServer>()
    private var sloop: Sloop? = null
    private var defaultSloopLauncher: SloopLauncher? = null

    constructor()

    // for tests only
    @NonInjectable
    constructor(sloopLauncher: SloopLauncher) {
        this.defaultSloopLauncher = sloopLauncher
    }

    init {
        registerListeners()
    }

    private fun registerListeners() {
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(GlobalConfigurationListener.TOPIC, object : GlobalConfigurationListener.Adapter() {
                override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
                    connectionsUpdated(newSettings.serverConnections)
                    val changedConnections = newSettings.serverConnections.filter { connection ->
                        val previousConnection = previousSettings.getServerConnectionByName(connection.name)
                        previousConnection.isPresent && !connection.hasSameCredentials(previousConnection.get())
                    }
                    credentialsChanged(changedConnections)
                }

                override fun changed(serverList: List<ServerConnection>) {
                    connectionsUpdated(serverList)
                }
            })
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                override fun projectClosing(project: Project) {
                    this@BackendService.projectClosed(project)
                }
            })

    }

    private fun <T> requestFromBackend(action: (SonarLintRpcServer) -> CompletableFuture<T>): CompletableFuture<T> {
        return ensureBackendInitialized().thenComposeAsync(action)
    }

    private fun notifyBackend(action: (SonarLintRpcServer) -> Unit) {
        ensureBackendInitialized().thenAcceptAsync(action)
    }

    private fun ensureBackendInitialized(): CompletableFuture<SonarLintRpcServer> {
        if (!initializationTriedOnce.getAndSet(true)) {
            if (ApplicationManager.getApplication().isUnitTestMode) {
                // workaround for tests as tasks are executed on UI thread
                runOnPooledThread {
                    ProgressManager.getInstance().run(createServiceStartingTask())
                }
            } else {
                // this will run in a background task
                ProgressManager.getInstance().run(createServiceStartingTask())
            }
        }
        return backendFuture
    }

    private fun createServiceStartingTask(): Task.Backgroundable {
        return object : Task.Backgroundable(null, "Starting SonarLint service...", false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val sloop = startSloopProcess()
                    this@BackendService.sloop = sloop
                    getService(GlobalLogOutput::class.java).log("Migrating the storage...", ClientLogOutput.Level.INFO)
                    migrateStoragePath()
                    getService(GlobalLogOutput::class.java).log("Listening for SonarLint service exit...", ClientLogOutput.Level.INFO)
                    listenForProcessExit(sloop)
                    getService(GlobalLogOutput::class.java).log("Initializing the SonarLint service...", ClientLogOutput.Level.INFO)
                    initRpcServer(sloop.rpcServer).get(1, TimeUnit.MINUTES)
                    getService(GlobalLogOutput::class.java).log("SonarLint service initialized...", ClientLogOutput.Level.INFO)
                    backendFuture.complete(sloop.rpcServer)
                } catch (t: TimeoutException) {
                    GlobalLogOutput.get().log("The 'Starting SonarLint service...' task timed out, please capture thread dumps of the 'SonarLintServerCli' process and report the problem to the SonarLint maintainers", ClientLogOutput.Level.ERROR)
                    handleSloopExited()
                    backendFuture.cancel(true)
                } catch (t: Throwable) {
                    GlobalLogOutput.get().logError("Cannot start the SonarLint service", t)
                    handleSloopExited()
                    backendFuture.cancel(true)
                }
            }
        }
    }

    private fun startSloopProcess(): Sloop {
        // SLI-1330
        val lsp4jLogger = Logger.getLogger("org.eclipse.lsp4j.jsonrpc.RemoteEndpoint")
        lsp4jLogger.filter = Filter { logRecord ->
            return@Filter if (logRecord.level == Level.SEVERE) {
                logRecord.level = Level.OFF
                GlobalLogOutput.get().logError(logRecord.message, logRecord.thrown)
                false
            } else true
        }
        getService(GlobalLogOutput::class.java).log("Starting the SonarLint service process...", ClientLogOutput.Level.INFO)
        val sloopLauncher = this.defaultSloopLauncher ?: SloopLauncher(SonarLintIntelliJClient)
        val jreHomePath = System.getProperty("java.home")!!
        val sloopPath = getService(SonarLintPlugin::class.java).path.resolve("sloop")
        getService(GlobalLogOutput::class.java).log("Listing SonarLint service files:", ClientLogOutput.Level.INFO)
        sloopPath.toFile().walkTopDown().forEach { file ->
            getService(GlobalLogOutput::class.java).log(file.absolutePath, ClientLogOutput.Level.INFO)
        }
        return sloopLauncher.start(sloopPath, Paths.get(jreHomePath))
    }

    private fun listenForProcessExit(sloopProcess: Sloop) {
        sloopProcess.onExit().thenAcceptAsync { handleSloopExited() }
    }

    private fun handleSloopExited() {
        getService(EngineManager::class.java).stopAllEngines(true)
        ProjectManager.getInstance().openProjects.forEach { project ->
            runOnUiThread(project) {
                getService(project, SonarLintToolWindow::class.java).refreshViews()
            }
        }
        projectLessNotification(
            null,
            SONARLINT_ERROR_MSG,
            NotificationType.ERROR,
            RestartBackendNotificationAction()
        )
    }

    private fun initRpcServer(rpcServer: SonarLintRpcServer): CompletableFuture<Void> {
        val serverConnections = getGlobalSettings().serverConnections
        val sonarCloudConnections =
            serverConnections.filter { it.isSonarCloud }.map { toSonarCloudBackendConnection(it) }
        val sonarQubeConnections =
            serverConnections.filter { !it.isSonarCloud }.map { toSonarQubeBackendConnection(it) }
        val nodejsPath = getGlobalSettings().nodejsPath
        val nonDefaultRpcRulesConfigurationByKey =
            getGlobalSettings().rulesByKey.mapValues { StandaloneRuleConfigDto(it.value.isActive, it.value.params) }

        return rpcServer.initialize(
            InitializeParams(
                ClientConstantInfoDto(
                    ApplicationInfo.getInstance().versionName,
                    "SonarLint IntelliJ " + getService(SonarLintPlugin::class.java).version
                ),
                getTelemetryConstantAttributes(),
                getHttpConfiguration(),
                getSonarCloudAlternativeEnvironment(),
                //TODO Create a ticket and provide right parameter for telemetry enabled
                FeatureFlagsDto(true, true, true, true, true, true, false, true, true),
                getLocalStoragePath(),
                SonarLintEngineFactory.getWorkDir(),
                EnabledLanguages.findEmbeddedPlugins(),
                EnabledLanguages.getEmbeddedPluginsForConnectedMode(),
                EnabledLanguages.enabledLanguagesInStandaloneMode,
                EnabledLanguages.extraEnabledLanguagesInConnectedMode,
                sonarQubeConnections,
                sonarCloudConnections,
                null,
                nonDefaultRpcRulesConfigurationByKey,
                getGlobalSettings().isFocusOnNewCode,
                if (nodejsPath.isBlank()) null else Paths.get(nodejsPath)
            )
        )
    }

    private fun getHttpConfiguration(): HttpConfigurationDto {
        return HttpConfigurationDto(
            SslConfigurationDto(getPathProperty("sonarlint.ssl.trustStorePath"), System.getProperty("sonarlint.ssl.trustStorePassword"),
                System.getProperty("sonarlint.ssl.trustStoreType"), getPathProperty("sonarlint.ssl.keyStorePath"), System.getProperty("sonarlint.ssl.keyStorePassword"),
                System.getProperty("sonarlint.ssl.keyStoreType")),
            getTimeoutProperty("sonarlint.http.connectTimeout"), getTimeoutProperty("sonarlint.http.socketTimeout"), getTimeoutProperty("sonarlint.http.connectionRequestTimeout"),
            getTimeoutProperty("sonarlint.http.responseTimeout"))
    }

    private fun getSonarCloudAlternativeEnvironment(): SonarCloudAlternativeEnvironmentDto? {
        val sonarCloudUrl = System.getProperty("sonarlint.internal.sonarcloud.url")
        val sonarCloudWebSocketUrl = System.getProperty("sonarlint.internal.sonarcloud.websocket.url")
        if (sonarCloudUrl != null && sonarCloudWebSocketUrl != null) {
            return SonarCloudAlternativeEnvironmentDto(URI.create(sonarCloudUrl), URI.create(sonarCloudWebSocketUrl))
        }
        return null
    }

    private fun getPathProperty(propertyName: String): Path? {
        val property = System.getProperty(propertyName)
        return property?.let { Paths.get(it) }
    }

    private fun getTimeoutProperty(propertyName: String): Duration? {
        val property = System.getProperty(propertyName)
        return property?.let { Duration.parse(it) }
    }

    private fun getTelemetryConstantAttributes() =
        TelemetryClientConstantAttributesDto("idea", "SonarLint IntelliJ", getService(SonarLintPlugin::class.java).version, getIdeVersionForTelemetry(), mapOf("intellij" to mapOf("jcefSupported" to JBCefApp.isSupported())))

    private fun getIdeVersionForTelemetry(): String {
        var ideVersion: String
        val appInfo = ApplicationInfo.getInstance()
        ideVersion = appInfo.versionName + " " + appInfo.fullVersion
        val edition = ApplicationNamesInfo.getInstance().editionName
        if (edition != null) {
            ideVersion += " ($edition)"
        }
        return ideVersion
    }

    fun notifyTelemetry(action: (TelemetryRpcService) -> Unit) {
        return notifyBackend { action(it.telemetryService) }
    }

    fun isTelemetryEnabled(): CompletableFuture<Boolean> {
        return requestFromBackend { it.telemetryService.status }.thenApplyAsync { status -> status.isEnabled }
    }

    fun getAllProjects(server: ServerConnection): CompletableFuture<GetAllProjectsResponse> {
        val credentials: Either<TokenDto, UsernamePasswordDto> = server.token?.let { Either.forLeft(TokenDto(server.token)) }
            ?: Either.forRight(UsernamePasswordDto(server.login, server.password))
        val params: GetAllProjectsParams = if (server.isSonarCloud) {
            GetAllProjectsParams(TransientSonarCloudConnectionDto(server.organizationKey, credentials))
        } else {
            GetAllProjectsParams(TransientSonarQubeConnectionDto(server.hostUrl, credentials))
        }

        return requestFromBackend { it.connectionService.getAllProjects(params) }
    }

    /**
     * SLI-657
     */
    private fun migrateStoragePath() {
        val oldPath = Paths.get(PathManager.getConfigPath()).resolve("sonarlint/storage")
        val newPath = getLocalStoragePath()
        if (Files.exists(oldPath) && !Files.exists(newPath)) {
            try {
                FileUtils.moveDirectory(oldPath.toFile(), newPath.toFile())
            } catch (e: IOException) {
                getService(GlobalLogOutput::class.java).logError("Unable to migrate storage", e)
            } finally {
                FileUtils.deleteQuietly(oldPath.toFile())
            }
        }
    }

    private fun getLocalStoragePath(): Path = Paths.get(PathManager.getSystemPath()).resolve("sonarlint/storage")

    fun connectionsUpdated(serverConnections: List<ServerConnection>) {
        val scConnections = serverConnections.filter { it.isSonarCloud }.map { toSonarCloudBackendConnection(it) }
        val sqConnections = serverConnections.filter { !it.isSonarCloud }.map { toSonarQubeBackendConnection(it) }
        notifyBackend { it.connectionService.didUpdateConnections(DidUpdateConnectionsParams(sqConnections, scConnections)) }
    }

    private fun credentialsChanged(connections: List<ServerConnection>) {
        connections.forEach { connection -> notifyBackend { it.connectionService.didChangeCredentials(DidChangeCredentialsParams(connection.name)) } }
    }

    private fun toSonarQubeBackendConnection(createdConnection: ServerConnection): SonarQubeConnectionConfigurationDto {
        return SonarQubeConnectionConfigurationDto(
            createdConnection.name,
            createdConnection.hostUrl,
            createdConnection.isDisableNotifications
        )
    }

    private fun toSonarCloudBackendConnection(createdConnection: ServerConnection): SonarCloudConnectionConfigurationDto {
        return SonarCloudConnectionConfigurationDto(
            createdConnection.name,
            createdConnection.organizationKey!!,
            createdConnection.isDisableNotifications
        )
    }

    fun projectOpened(project: Project) {
        val binding = getService(project, ProjectBindingManager::class.java).binding
        notifyBackend {
            it.configurationService.didAddConfigurationScopes(
                DidAddConfigurationScopesParams(
                    listOf(
                        toBackendConfigurationScope(
                            project,
                            binding
                        )
                    )
                )
            )
        }
        refreshTaintVulnerabilities(project)
    }

    internal fun projectClosed(project: Project) {
        ModuleManager.getInstance(project).modules.forEach { moduleRemoved(it) }
        val projectId = projectId(project)
        notifyBackend { it.configurationService.didRemoveConfigurationScope(DidRemoveConfigurationScopeParams(projectId)) }
    }

    private fun toBackendConfigurationScope(project: Project, binding: ProjectBinding?) =
        ConfigurationScopeDto(projectId(project), null, true, project.name,
            BindingConfigurationDto(binding?.connectionName, binding?.projectKey, areBindingSuggestionsDisabledFor(project)))


    fun projectBound(project: Project, newBinding: ProjectBinding) {
        notifyBackend {
            it.configurationService.didUpdateBinding(
                DidUpdateBindingParams(
                    projectId(project), BindingConfigurationDto(
                    newBinding.connectionName, newBinding.projectKey, areBindingSuggestionsDisabledFor(project)
                )
                )
            )
        }
        newBinding.moduleBindingsOverrides.forEach { (module, projectKey) ->
            val moduleId = moduleId(module)
            notifyBackend {
                it.configurationService.didUpdateBinding(
                    DidUpdateBindingParams(
                        moduleId, BindingConfigurationDto(
                        // we don't want binding suggestions for modules
                        newBinding.connectionName, projectKey, true
                    )
                    )
                )
            }
        }
        refreshTaintVulnerabilities(project)
    }

    fun projectUnbound(project: Project) {
        notifyBackend {
            it.configurationService.didUpdateBinding(
                DidUpdateBindingParams(
                    projectId(project), BindingConfigurationDto(null, null, areBindingSuggestionsDisabledFor(project))
                )
            )
        }
        refreshTaintVulnerabilities(project)
    }

    fun modulesAdded(project: Project, modules: List<Module>) {
        val projectBinding = getService(project, ProjectBindingManager::class.java).binding
        notifyBackend {
            it.configurationService.didAddConfigurationScopes(
                DidAddConfigurationScopesParams(
                    modules.map { module -> toConfigurationScope(module, projectBinding) }
                )
            )
        }
    }

    private fun toConfigurationScope(module: Module, projectBinding: ProjectBinding?): ConfigurationScopeDto {
        val moduleProjectKey = getService(module, ModuleBindingManager::class.java).configuredProjectKey
        return ConfigurationScopeDto(moduleId(module), projectId(module.project), true, module.name,
            BindingConfigurationDto(projectBinding?.connectionName, projectBinding?.let { moduleProjectKey }, true))
    }

    fun moduleRemoved(module: Module) {
        val moduleId = moduleId(module)
        notifyBackend { it.configurationService.didRemoveConfigurationScope(DidRemoveConfigurationScopeParams(moduleId)) }
    }

    fun moduleUnbound(module: Module) {
        val moduleId = moduleId(module)
        notifyBackend {
            it.configurationService.didUpdateBinding(
                DidUpdateBindingParams(
                    moduleId, BindingConfigurationDto(
                        // we don't want binding suggestions for modules
                        null, null, true
                    )
                )
            )
        }
    }

    private fun areBindingSuggestionsDisabledFor(project: Project) =
        !getSettingsFor(project).isBindingSuggestionsEnabled

    fun bindingSuggestionsDisabled(project: Project) {
        val binding = getService(project, ProjectBindingManager::class.java).binding
        notifyBackend {
            it.configurationService.didUpdateBinding(
                DidUpdateBindingParams(
                    projectId(project),
                    BindingConfigurationDto(binding?.connectionName, binding?.projectKey, true)
                )
            )
        }
    }

    fun getActiveRuleDetails(module: Module, ruleKey: String, contextKey: String?): CompletableFuture<GetEffectiveRuleDetailsResponse> {
        val moduleId = moduleId(module)
        return requestFromBackend {
            it.rulesService.getEffectiveRuleDetails(
                GetEffectiveRuleDetailsParams(
                    moduleId,
                    ruleKey,
                    contextKey
                )
            )
        }
    }

    fun getListAllStandaloneRulesDefinitions(): CompletableFuture<ListAllStandaloneRulesDefinitionsResponse> {
        return requestFromBackend { it.rulesService.listAllStandaloneRulesDefinitions() }
    }

    fun getSharedConnectedModeConfigFileContents(configScopeId: String): CompletableFuture<GetSharedConnectedModeConfigFileResponse> {
        return requestFromBackend {
            it.bindingService.getSharedConnectedModeConfigFileContents(
                GetSharedConnectedModeConfigFileParams(configScopeId)
            )
        }
    }

    fun getStandaloneRuleDetails(params: GetStandaloneRuleDescriptionParams): CompletableFuture<GetStandaloneRuleDescriptionResponse> {
        return requestFromBackend { it.rulesService.getStandaloneRuleDetails(params) }
    }

    fun updateStandaloneRulesConfiguration(nonDefaultRulesConfigurationByKey: Map<String, SonarLintGlobalSettings.Rule>) {
        val nonDefaultRpcRulesConfigurationByKey = nonDefaultRulesConfigurationByKey.mapValues { StandaloneRuleConfigDto(it.value.isActive, it.value.params) }
        notifyBackend { it.rulesService.updateStandaloneRulesConfiguration(UpdateStandaloneRulesConfigurationParams(nonDefaultRpcRulesConfigurationByKey)) }
    }

    fun helpGenerateUserToken(serverUrl: String, isSonarCloud: Boolean): CompletableFuture<HelpGenerateUserTokenResponse> {
        return requestFromBackend { it.connectionService.helpGenerateUserToken(HelpGenerateUserTokenParams(serverUrl, isSonarCloud)) }
    }

    fun openHotspotInBrowser(module: Module, hotspotKey: String) {
        val configScopeId = moduleId(module)
        notifyBackend {
            it.hotspotService.openHotspotInBrowser(
                OpenHotspotInBrowserParams(
                    configScopeId,
                    hotspotKey
                )
            )
        }
    }

    fun checkLocalSecurityHotspotDetectionSupported(project: Project): CompletableFuture<CheckLocalDetectionSupportedResponse> {
        return requestFromBackend {
            it.hotspotService.checkLocalDetectionSupported(
                CheckLocalDetectionSupportedParams(
                    projectId(project)
                )
            )
        }
    }

    fun changeStatusForHotspot(module: Module, hotspotKey: String, newStatus: HotspotStatus): CompletableFuture<Void> {
        val moduleId = moduleId(module)
        return requestFromBackend {
            it.hotspotService.changeStatus(
                ChangeHotspotStatusParams(
                    moduleId,
                    hotspotKey,
                    newStatus
                )
            )
        }
    }

    fun markAsResolved(module: Module, issueKey: String, newStatus: IssueResolutionStatus, isTaintVulnerability: Boolean): CompletableFuture<Void> {
        val moduleId = moduleId(module)
        return requestFromBackend {
            it.issueService.changeStatus(
                ChangeIssueStatusParams(
                    moduleId,
                    issueKey,
                    ResolutionStatus.valueOf(newStatus.name),
                    isTaintVulnerability
                )
            )
        }
    }

    fun reopenIssue(module: Module, issueId: String, isTaintIssue: Boolean): CompletableFuture<ReopenIssueResponse> {
        val moduleId = moduleId(module)
        return requestFromBackend { it.issueService.reopenIssue(ReopenIssueParams(moduleId, issueId, isTaintIssue)) }
    }

    fun addCommentOnIssue(module: Module, issueKey: String, comment: String): CompletableFuture<Void> {
        val moduleId = moduleId(module)
        return requestFromBackend { it.issueService.addComment(AddIssueCommentParams(moduleId, issueKey, comment)) }
    }

    fun checkStatusChangePermitted(connectionId: String, hotspotKey: String): CompletableFuture<CheckStatusChangePermittedResponse> {
        return requestFromBackend { it.hotspotService.checkStatusChangePermitted(CheckStatusChangePermittedParams(connectionId, hotspotKey)) }
    }

    fun checkIssueStatusChangePermitted(
        connectionId: String,
        issueKey: String,
    ): CompletableFuture<issueCheckStatusChangePermittedResponse> {
        return requestFromBackend {
            it.issueService.checkStatusChangePermitted(
                issueCheckStatusChangePermittedParams(connectionId, issueKey)
            )
        }
    }

    fun didVcsRepoChange(project: Project) {
        notifyBackend { it.sonarProjectBranchService.didVcsRepositoryChange(DidVcsRepositoryChangeParams(projectId(project))) }
    }

    fun checkSmartNotificationsSupported(server: ServerConnection): CompletableFuture<CheckSmartNotificationsSupportedResponse> {
        val credentials: Either<TokenDto, UsernamePasswordDto> = server.token?.let { Either.forLeft(TokenDto(server.token)) }
            ?: Either.forRight(UsernamePasswordDto(server.login, server.password))
        val params: CheckSmartNotificationsSupportedParams = if (server.isSonarCloud) {
            CheckSmartNotificationsSupportedParams(TransientSonarCloudConnectionDto(server.organizationKey, credentials))
        } else {
            CheckSmartNotificationsSupportedParams(TransientSonarQubeConnectionDto(server.hostUrl, credentials))
        }
        return requestFromBackend { it.connectionService.checkSmartNotificationsSupported(params) }
    }

    fun validateConnection(server: ServerConnection): CompletableFuture<ValidateConnectionResponse> {
        val credentials: Either<TokenDto, UsernamePasswordDto> = server.token?.let { Either.forLeft(TokenDto(server.token)) }
            ?: Either.forRight(UsernamePasswordDto(server.login, server.password))
        val params: ValidateConnectionParams = if (server.isSonarCloud) {
            ValidateConnectionParams(TransientSonarCloudConnectionDto(server.organizationKey, credentials))
        } else {
            ValidateConnectionParams(TransientSonarQubeConnectionDto(server.hostUrl, credentials))
        }
        return requestFromBackend { it.connectionService.validateConnection(params) }
    }

    fun listUserOrganizations(server: ServerConnection): CompletableFuture<ListUserOrganizationsResponse> {
        val credentials: Either<TokenDto, UsernamePasswordDto> = server.token?.let { Either.forLeft(TokenDto(server.token)) }
            ?: Either.forRight(UsernamePasswordDto(server.login, server.password))
        val params = ListUserOrganizationsParams(credentials)
        return requestFromBackend { it.connectionService.listUserOrganizations(params) }
    }

    fun getOrganization(server: ServerConnection, organizationKey: String): CompletableFuture<GetOrganizationResponse> {
        val credentials: Either<TokenDto, UsernamePasswordDto> = server.token?.let { Either.forLeft(TokenDto(server.token)) }
            ?: Either.forRight(UsernamePasswordDto(server.login, server.password))
        val params = GetOrganizationParams(credentials, organizationKey)
        return requestFromBackend { it.connectionService.getOrganization(params) }
    }

    fun trackWithServerIssues(
        module: Module,
        liveIssuesByFile: Map<VirtualFile, Collection<LiveIssue>>,
        shouldFetchIssuesFromServer: Boolean,
    ) {
        val relativePathByVirtualFile = getRelativePaths(module.project, liveIssuesByFile.keys)
        val virtualFileByRelativePath = relativePathByVirtualFile.map { Pair(it.value, it.key) }.toMap()
        val rawIssuesByRelativePath =
            liveIssuesByFile
                .filterKeys { file -> relativePathByVirtualFile.containsKey(file) }
                .entries.associate { (file, issues) ->
                    relativePathByVirtualFile[file]!! to issues.map {
                        val textRangeWithHashDto = toTextRangeWithHashDto(module.project, it)
                        ClientTrackedFindingDto(
                            it.backendId,
                            it.serverFindingKey,
                            textRangeWithHashDto,
                            textRangeWithHashDto?.let { range -> LineWithHashDto(range.startLine, it.lineHashString!!) },
                            it.ruleKey,
                            it.message
                        )
                    }
                }

        try {
            val moduleId = moduleId(module)
            requestFromBackend {
                it.issueTrackingService.trackWithServerIssues(
                    TrackWithServerIssuesParams(
                        moduleId,
                        rawIssuesByRelativePath,
                        shouldFetchIssuesFromServer
                    )
                )
            }.thenAcceptAsync { response ->
                response.issuesByIdeRelativePath.forEach { (ideRelativePath, trackedIssues) ->
                    val file = virtualFileByRelativePath[ideRelativePath] ?: return@forEach
                    val liveIssues = liveIssuesByFile[file] ?: return@forEach
                    liveIssues.zip(trackedIssues).forEach { (liveIssue, serverOrLocalIssue) ->
                        if (serverOrLocalIssue.isLeft) {
                            val serverIssue = serverOrLocalIssue.left
                            liveIssue.backendId = serverIssue.id
                            liveIssue.introductionDate = serverIssue.introductionDate
                            liveIssue.serverFindingKey = serverIssue.serverKey
                            liveIssue.isResolved = serverIssue.isResolved
                            serverIssue.overriddenSeverity?.let { liveIssue.setSeverity(it) }
                            liveIssue.setType(serverIssue.type)
                            liveIssue.setOnNewCode(serverIssue.isOnNewCode)
                        } else {
                            val localOnlyIssue = serverOrLocalIssue.right
                            liveIssue.backendId = localOnlyIssue.id
                            liveIssue.isResolved = localOnlyIssue.resolutionStatus != null
                            liveIssue.setOnNewCode(true)
                        }
                    }
                }
            }
                .join()
        } catch (e: CancellationException) {
            SonarLintConsole.get(module.project).debug("The request to match issues has been canceled")
        }
    }

    fun trackWithServerHotspots(
        module: Module,
        liveHotspotsByFile: Map<VirtualFile, Collection<LiveSecurityHotspot>>,
        shouldFetchHotspotsFromServer: Boolean,
    ) {
        val relativePathByVirtualFile = getRelativePaths(module.project, liveHotspotsByFile.keys)
        val virtualFileByRelativePath = relativePathByVirtualFile.map { Pair(it.value, it.key) }.toMap()
        val rawHotspotsByRelativePath =
            liveHotspotsByFile
                .filterKeys { file -> relativePathByVirtualFile.containsKey(file) }
                .entries.associate { (file, hotspots) ->
                    relativePathByVirtualFile[file]!! to hotspots.map {
                        val textRangeWithHashDto = toTextRangeWithHashDto(module.project, it)
                        ClientTrackedFindingDto(
                            it.backendId,
                            it.serverFindingKey,
                            textRangeWithHashDto,
                            textRangeWithHashDto?.let { range -> LineWithHashDto(range.startLine, it.lineHashString!!) },
                            it.ruleKey,
                            it.message
                        )
                    }
                }

        try {
            val moduleId = moduleId(module)
            requestFromBackend {
                it.securityHotspotMatchingService.matchWithServerSecurityHotspots(
                    MatchWithServerSecurityHotspotsParams(
                        moduleId,
                        rawHotspotsByRelativePath,
                        shouldFetchHotspotsFromServer
                    )
                )
            }.thenAcceptAsync { response ->
                response.securityHotspotsByIdeRelativePath.forEach { (serverRelativePath, trackedHotspots) ->
                    val file = virtualFileByRelativePath[serverRelativePath] ?: return@forEach
                    val liveHotspots = liveHotspotsByFile[file] ?: return@forEach
                    liveHotspots.zip(trackedHotspots).forEach { (liveHotspot, serverOrLocalHotspot) ->
                        if (serverOrLocalHotspot.isLeft) {
                            val serverHotspot = serverOrLocalHotspot.left
                            liveHotspot.backendId = serverHotspot.id
                            liveHotspot.introductionDate = serverHotspot.introductionDate
                            liveHotspot.serverFindingKey = serverHotspot.serverKey
                            liveHotspot.isResolved = serverHotspot.status == HotspotStatus.FIXED || serverHotspot.status == HotspotStatus.SAFE
                            liveHotspot.setStatus(serverHotspot.status)
                            liveHotspot.setOnNewCode(serverHotspot.isOnNewCode)
                        } else {
                            val localOnlyIssue = serverOrLocalHotspot.right
                            liveHotspot.backendId = localOnlyIssue.id
                            liveHotspot.setOnNewCode(true)
                        }
                    }
                }
            }
                .join()
        } catch (e: CancellationException) {
            SonarLintConsole.get(module.project).debug("The request to match Security Hotspots has been canceled")
        }
    }

    private fun toTextRangeWithHashDto(project: Project, finding: LiveFinding): TextRangeWithHashDto? {
        val rangeMarker = finding.range ?: return null
        if (!rangeMarker.isValid) {
            return null
        }
        return computeReadActionSafely(project) {
            val startLine = rangeMarker.document.getLineNumber(rangeMarker.startOffset)
            val startLineOffset = rangeMarker.startOffset - rangeMarker.document.getLineStartOffset(startLine)
            val endLine = rangeMarker.document.getLineNumber(rangeMarker.endOffset)
            val endLineOffset = rangeMarker.endOffset - rangeMarker.document.getLineStartOffset(endLine)
            TextRangeWithHashDto(startLine + 1, startLineOffset, endLine + 1, endLineOffset, finding.textRangeHashString)
        }
    }

    fun getNewCodePeriodText(project: Project): CompletableFuture<String> {
        // simplification as we ignore module bindings
        return requestFromBackend { it.newCodeService.getNewCodeDefinition(GetNewCodeDefinitionParams(projectId(project))) }
            .thenApplyAsync { response -> if (response.isSupported) response.description else "(unsupported new code definition)" }
            .exceptionally { "(unknown code period)" }
    }

    fun triggerTelemetryForFocusOnNewCode() {
        notifyBackend { it.newCodeService.didToggleFocus() }
    }

    fun restartBackendService() {
        if (isAlive()) {
            return
        }
        initializationTriedOnce.set(false)
        backendFuture = CompletableFuture()
        sloop = null
        ensureBackendInitialized().thenAcceptAsync { catchUpWithBackend(it) }
    }

    private fun catchUpWithBackend(rpcServer: SonarLintRpcServer) {
        ProjectManager.getInstance().openProjects.forEach { project ->
            runOnUiThread(project) {
                getService(project, SonarLintToolWindow::class.java).refreshViews()
            }

            val binding = getService(project, ProjectBindingManager::class.java).binding
            rpcServer.configurationService.didAddConfigurationScopes(
                DidAddConfigurationScopesParams(
                    listOf(
                        toBackendConfigurationScope(
                            project,
                            binding
                        )
                    )
                )
            )
            refreshTaintVulnerabilities(project)

            rpcServer.configurationService.didAddConfigurationScopes(
                DidAddConfigurationScopesParams(
                    ModuleManager.getInstance(project).modules.map { toConfigurationScope(it, binding) }
                )
            )
        }
    }

    companion object {
        fun projectId(project: Project) = project.projectFilePath ?: "DEFAULT_PROJECT"

        fun moduleId(module: Module): String {
            return getSettingsFor(module.project).moduleMapping[module.name] ?: module.name
        }

        fun findModule(configScopeId: String): Module? {
            return ProjectManager.getInstance().openProjects.firstNotNullOfOrNull { project ->
                val mapping = getSettingsFor(project).moduleMapping.filterValues { scopeId -> scopeId == configScopeId }.keys
                val currentModuleName = if (mapping.isNotEmpty()) mapping.first() else configScopeId
                return ModuleManager.getInstance(project).modules.firstOrNull { module -> module.name == currentModuleName }
            }
        }

        fun findProject(configScopeId: String): Project? {
            return ProjectManager.getInstance().openProjects.find { projectId(it) == configScopeId }
        }
    }

    override fun dispose() {
        backendFuture.thenAccept { it.shutdown() }
    }

    fun refreshTaintVulnerabilities(project: Project) {
        requestFromBackend { it.taintVulnerabilityTrackingService.listAll(ListAllParams(projectId(project), true)) }
            .thenApplyAsync { response ->
                val localTaintVulnerabilities = computeReadActionSafely(project) {
                    val taintVulnerabilityMatcher = TaintVulnerabilityMatcher(project)
                    response.taintVulnerabilities.map { taintVulnerabilityMatcher.match(it) }
                } ?: return@thenApplyAsync
                runOnUiThread(project) {
                    getService(project, SonarLintToolWindow::class.java).populateTaintVulnerabilitiesTab(localTaintVulnerabilities)
                }
            }
    }

    fun getExcludedFiles(module: Module, files: Collection<VirtualFile>): List<VirtualFile> {
        val filesByUri = files.associateBy { VirtualFileUtils.toURI(it) }
        return try {
            val moduleId = moduleId(module)
            requestFromBackend {
                it.fileService.getFilesStatus(
                    GetFilesStatusParams(
                        mapOf(
                            moduleId to filesByUri.keys.filterNotNull().toList()
                        )
                    )
                )
            }
                .thenApplyAsync { response -> response.fileStatuses.filterValues { it.isExcluded }.keys.mapNotNull { filesByUri[it] } }
                .join()
        } catch (e: CancellationException) {
            SonarLintConsole.get(module.project).debug("The request to retrieve file exclusions has been canceled")
            emptyList()
        } catch (e: Exception) {
            SonarLintConsole.get(module.project).error("Error when retrieving excluded files", e)
            emptyList()
        }
    }

    fun createEngine(engineConfiguration: EngineConfiguration, connectionId: String?): SonarLintAnalysisEngine {
        // this will throw if the backend is not ready after 1min. Should be called only from Analysis thread
        // the concept of engine will soon disappear for clients
        val initializedBackend = backendFuture.get(1, TimeUnit.MINUTES)
        return SonarLintAnalysisEngine(engineConfiguration, initializedBackend, connectionId)
    }

    fun getAutoDetectedNodeJs(): CompletableFuture<NodeJsSettings?> {
        return requestFromBackend { it.analysisService.autoDetectedNodeJs }.thenApplyAsync { response ->
            response.details?.let { NodeJsSettings(it.path, it.version) }
        }
    }

    fun updateFileSystem(filesByModule: Map<Module, List<ClientModuleFileEvent>>) {
        val deletedFileUris = filesByModule.values
            .flatMap { it.filter { event -> event.type() == ModuleFileEvent.Type.DELETED } }
            .mapNotNull { VirtualFileUtils.toURI(it.target().getClientObject()) }
        val events = filesByModule.entries.flatMap { (module, event) ->
            event.filter { it.type() != ModuleFileEvent.Type.DELETED }
                .map {
                    ClientFileDto(
                        it.target().uri(),
                        Paths.get(it.target().relativePath()),
                        moduleId(module),
                        it.target().isTest,
                        it.target().charset.toString(),
                        Paths.get(it.target().path),
                        null
                    )
                }
        }
        notifyBackend { it.fileService.didUpdateFileSystem(DidUpdateFileSystemParams(deletedFileUris, events)) }
    }

    fun isAlive(): Boolean {
        return sloop?.isAlive == true
    }

}
