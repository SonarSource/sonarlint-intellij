/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.serviceContainer.NonInjectable
import com.jetbrains.rd.util.firstOrNull
import org.apache.commons.io.FileUtils
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.sonarlint.intellij.SonarLintIntelliJClient
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.vcs.VcsService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.telemetry.TelemetryManagerProvider
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.SonarLintBackendImpl
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend
import org.sonarsource.sonarlint.core.clientapi.backend.HostInfoDto
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams
import org.sonarsource.sonarlint.core.clientapi.backend.branch.DidChangeActiveSonarProjectBranchParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenResponse
import org.sonarsource.sonarlint.core.clientapi.backend.connection.check.CheckSmartNotificationsSupportedParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.check.CheckSmartNotificationsSupportedResponse
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarCloudConnectionDto
import org.sonarsource.sonarlint.core.clientapi.backend.connection.common.TransientSonarQubeConnectionDto
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ChangeHotspotStatusParams
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedParams
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedResponse
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedParams
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedResponse
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsResponse
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ListAllStandaloneRulesDefinitionsResponse
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto
import org.sonarsource.sonarlint.core.http.HttpClient
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse as CheckIssueStatusChangePermittedResponse

@Service(Service.Level.APP)
class BackendService @NonInjectable constructor(private val backend: SonarLintBackend) : Disposable {
    constructor() : this(SonarLintBackendImpl(SonarLintIntelliJClient))

    private val initializedBackend: SonarLintBackend by lazy {
        migrateStoragePath()
        val serverConnections = getGlobalSettings().serverConnections
        val sonarCloudConnections =
            serverConnections.filter { it.isSonarCloud }.map { toSonarCloudBackendConnection(it) }
        val sonarQubeConnections =
            serverConnections.filter { !it.isSonarCloud }.map { toSonarQubeBackendConnection(it) }
        backend.initialize(
            InitializeParams(
                HostInfoDto(ApplicationInfo.getInstance().versionName),
                TelemetryManagerProvider.TELEMETRY_PRODUCT_KEY,
                getLocalStoragePath(),
                SonarLintEngineFactory.getWorkDir(),
                EmbeddedPlugins.findEmbeddedPlugins(),
                EmbeddedPlugins.getEmbeddedPluginsForConnectedMode(),
                EmbeddedPlugins.enabledLanguagesInStandaloneMode,
                EmbeddedPlugins.enabledLanguagesInConnectedMode,
                true,
                sonarQubeConnections,
                sonarCloudConnections,
                null,
                true,
                mapOf(),
                true,
                SonarLintUtils.isTaintVulnerabilitiesEnabled(),
                true,
                "SonarLint IntelliJ " + getService(SonarLintPlugin::class.java).version
            )
        ).thenRun {
            ApplicationManager.getApplication().messageBus.connect()
                .subscribe(GlobalConfigurationListener.TOPIC, object : GlobalConfigurationListener.Adapter() {
                    override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
                        connectionsUpdated(newSettings.serverConnections)
                    }

                    override fun changed(serverList: MutableList<ServerConnection>) {
                        connectionsUpdated(serverList)
                    }
                })
            ApplicationManager.getApplication().messageBus.connect()
                .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {

                    override fun projectClosing(project: Project) {
                        this@BackendService.projectClosed(project)
                    }
                })
        }.get()
        backend
    }

    fun getHttpClient(connectionId: String): HttpClient {
        return initializedBackend.getHttpClient(connectionId)
    }

    fun getHttpClientNoAuth(): HttpClient {
        return initializedBackend.getHttpClientNoAuth()
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
        backend.connectionService.didUpdateConnections(DidUpdateConnectionsParams(sqConnections, scConnections))
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
        initializedBackend.configurationService.didAddConfigurationScopes(
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

    internal fun projectClosed(project: Project) {
        ModuleManager.getInstance(project).modules.forEach { moduleRemoved(it) }
        initializedBackend.configurationService.didRemoveConfigurationScope(DidRemoveConfigurationScopeParams(projectId(project)))
    }

    private fun toBackendConfigurationScope(project: Project, binding: ProjectBinding?) = ConfigurationScopeDto(
        projectId(project), null, true, project.name, BindingConfigurationDto(
            binding?.connectionName, binding?.projectKey, areBindingSuggestionsDisabledFor(project)
        )
    )


    fun projectBound(project: Project, newBinding: ProjectBinding) {
        initializedBackend.configurationService.didUpdateBinding(
            DidUpdateBindingParams(
                projectId(project), BindingConfigurationDto(
                    newBinding.connectionName, newBinding.projectKey, areBindingSuggestionsDisabledFor(project)
                )
            )
        )
        newBinding.moduleBindingsOverrides.forEach { (module, projectKey) ->
            initializedBackend.configurationService.didUpdateBinding(
                DidUpdateBindingParams(
                    moduleId(module), BindingConfigurationDto(
                        // we don't want binding suggestions for modules
                        newBinding.connectionName, projectKey, true
                    )
                )
            )
        }

    }

    fun projectUnbound(project: Project) {
        initializedBackend.configurationService.didUpdateBinding(
            DidUpdateBindingParams(
                projectId(project), BindingConfigurationDto(null, null, areBindingSuggestionsDisabledFor(project))
            )
        )

    }

    fun moduleAdded(module: Module) {
        val moduleProjectKey = getService(module, ModuleBindingManager::class.java).configuredProjectKey
        val projectBinding = getService(module.project, ProjectBindingManager::class.java).binding
        initializedBackend.configurationService.didAddConfigurationScopes(
            DidAddConfigurationScopesParams(
                listOf(
                    ConfigurationScopeDto(
                        moduleId(module), projectId(module.project), true, module.name, BindingConfigurationDto(
                            projectBinding?.connectionName, projectBinding?.let { moduleProjectKey }, true
                        )
                    )
                )
            )
        )
    }

    fun moduleRemoved(module: Module) {
        initializedBackend.configurationService.didRemoveConfigurationScope(DidRemoveConfigurationScopeParams(moduleId(module)))
        uniqueIdentifierForModules.remove(module)
    }

    fun moduleUnbound(module: Module) {
        initializedBackend.configurationService.didUpdateBinding(
            DidUpdateBindingParams(
                moduleId(module), BindingConfigurationDto(
                    // we don't want binding suggestions for modules
                    null, null, true
                )
            )
        )
    }

    private fun areBindingSuggestionsDisabledFor(project: Project) =
        !getSettingsFor(project).isBindingSuggestionsEnabled

    fun bindingSuggestionsDisabled(project: Project) {
        val binding = getService(project, ProjectBindingManager::class.java).binding
        initializedBackend.configurationService.didUpdateBinding(
            DidUpdateBindingParams(
                projectId(project),
                BindingConfigurationDto(binding?.connectionName, binding?.projectKey, true)
            )
        )
    }

    fun getActiveRuleDetails(module: Module, ruleKey: String, contextKey: String?): CompletableFuture<GetEffectiveRuleDetailsResponse> {
        return initializedBackend.rulesService.getEffectiveRuleDetails(
            GetEffectiveRuleDetailsParams(
                moduleId(module),
                ruleKey,
                contextKey
            )
        )
    }

    fun getListAllStandaloneRulesDefinitions(): CompletableFuture<ListAllStandaloneRulesDefinitionsResponse> {
        return initializedBackend.rulesService.listAllStandaloneRulesDefinitions()
    }

    fun helpGenerateUserToken(serverUrl: String, isSonarCloud: Boolean): CompletableFuture<HelpGenerateUserTokenResponse> {
        return initializedBackend.connectionService.helpGenerateUserToken(HelpGenerateUserTokenParams(serverUrl, isSonarCloud))
    }

    fun openHotspotInBrowser(module: Module, hotspotKey: String) {
        val branchName: String? = getService(module.project, VcsService::class.java).getServerBranchName(module)
        branchName?.let {
            val configScopeId = moduleId(module)
            initializedBackend.hotspotService.openHotspotInBrowser(
                OpenHotspotInBrowserParams(
                    configScopeId,
                    branchName,
                    hotspotKey
                )
            )
        }
    }

    fun checkLocalSecurityHotspotDetectionSupported(project: Project): CompletableFuture<CheckLocalDetectionSupportedResponse> {
        return initializedBackend.hotspotService.checkLocalDetectionSupported(
            CheckLocalDetectionSupportedParams(
                projectId(
                    project
                )
            )
        )
    }

    fun changeStatusForHotspot(module: Module, hotspotKey: String, newStatus: HotspotStatus): CompletableFuture<Void> {
        return initializedBackend.hotspotService.changeStatus(
            ChangeHotspotStatusParams(
                moduleId(module),
                hotspotKey,
                newStatus
            )
        )
    }

    fun markAsResolved(module: Module, issueKey: String, newStatus: IssueStatus, isTaintVulnerability: Boolean, ): CompletableFuture<Void> {
        return initializedBackend.issueService.changeStatus(ChangeIssueStatusParams(moduleId(module), issueKey, newStatus, isTaintVulnerability))
    }

    fun addCommentOnIssue(module: Module, issueKey: String, comment: String, ): CompletableFuture<Void> {
        return initializedBackend.issueService.addComment(AddIssueCommentParams(moduleId(module), issueKey, comment))
    }

    fun checkStatusChangePermitted(connectionId: String, hotspotKey: String): CompletableFuture<CheckStatusChangePermittedResponse> {
        return initializedBackend.hotspotService.checkStatusChangePermitted(CheckStatusChangePermittedParams(connectionId, hotspotKey))
    }

    fun checkIssueStatusChangePermitted(
        connectionId: String,
        issueKey: String,
    ): CompletableFuture<CheckIssueStatusChangePermittedResponse> {
        return initializedBackend.issueService.checkStatusChangePermitted(
                org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams(connectionId, issueKey)
            )
    }

    fun branchChanged(module: Module, newActiveBranchName: String) {
        initializedBackend.sonarProjectBranchService.didChangeActiveSonarProjectBranch(
            DidChangeActiveSonarProjectBranchParams(
                moduleId(module),
                newActiveBranchName
            )
        )
    }

    fun checkSmartNotificationsSupported(server: ServerConnection): CompletableFuture<CheckSmartNotificationsSupportedResponse> {
        val credentials: Either<TokenDto, UsernamePasswordDto> = server.token?.let { Either.forLeft(TokenDto(server.token)) }
            ?: Either.forRight(UsernamePasswordDto(server.login, server.password))
        val params: CheckSmartNotificationsSupportedParams = if (server.isSonarCloud) {
            CheckSmartNotificationsSupportedParams(TransientSonarCloudConnectionDto(server.organizationKey, credentials))
        } else {
            CheckSmartNotificationsSupportedParams(TransientSonarQubeConnectionDto(server.hostUrl, credentials))
        }
        return initializedBackend.connectionService.checkSmartNotificationsSupported(params)
    }

    fun validateConnection(server: ServerConnection): CompletableFuture<ValidateConnectionResponse> {
        val credentials: Either<TokenDto, UsernamePasswordDto> = server.token?.let { Either.forLeft(TokenDto(server.token)) }
            ?: Either.forRight(UsernamePasswordDto(server.login, server.password))
        val params: ValidateConnectionParams = if (server.isSonarCloud) {
            ValidateConnectionParams(TransientSonarCloudConnectionDto(server.organizationKey, credentials))
        } else {
            ValidateConnectionParams(TransientSonarQubeConnectionDto(server.hostUrl, credentials))
        }
        return initializedBackend.connectionService.validateConnection(params)
    }

    companion object {
        private var moduleCount = 1
        internal val uniqueIdentifierForModules = ConcurrentHashMap<Module, String>()
        fun projectId(project: Project) = project.projectFilePath ?: "DEFAULT_PROJECT"

        fun moduleId(module: Module): String {
            // there is no reliable unique identifier for modules, but a module is represented by a single object
            return uniqueIdentifierForModules.computeIfAbsent(module) { m -> m.name + "-" + moduleCount++ }
        }

        fun findModule(configScopeId: String): Module? {
            return uniqueIdentifierForModules.filter { it.value == configScopeId }.firstOrNull()?.key
        }

        fun findProject(configScopeId: String): Project? {
            return ProjectManager.getInstance().openProjects.find { projectId(it) == configScopeId }
        }
    }

    override fun dispose() {
        initializedBackend.shutdown()
    }
}
