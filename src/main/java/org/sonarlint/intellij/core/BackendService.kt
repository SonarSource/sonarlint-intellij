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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import org.apache.commons.io.FileUtils
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.sonarlint.intellij.SonarLintIntelliJClient
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.ui.ReadActionUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.vcs.VcsService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.ServerConnectionService
import org.sonarlint.intellij.config.global.SonarCloudConnection
import org.sonarlint.intellij.config.global.SonarQubeConnection
import org.sonarlint.intellij.config.global.wizard.PartialConnection
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.messages.ServerConnectionsListener
import org.sonarlint.intellij.telemetry.TelemetryManagerProvider
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.ProjectUtils.getRelativePaths
import org.sonarsource.sonarlint.core.SonarLintBackendImpl
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend
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
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidChangeCredentialsParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.GetOrganizationParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.GetOrganizationResponse
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.ListUserOrganizationsParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.ListUserOrganizationsResponse
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.ChangeHotspotStatusParams
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedParams
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckLocalDetectionSupportedResponse
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedParams
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.CheckStatusChangePermittedResponse
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.OpenHotspotInBrowserParams
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.ClientInfoDto
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.FeatureFlagsDto
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenIssueParams
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ReopenIssueResponse
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ResolutionStatus
import org.sonarsource.sonarlint.core.clientapi.backend.newcode.GetNewCodeDefinitionParams
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsParams
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetEffectiveRuleDetailsResponse
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionParams
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetStandaloneRuleDescriptionResponse
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ListAllStandaloneRulesDefinitionsResponse
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ClientTrackedFindingDto
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LineWithHashDto
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.MatchWithServerSecurityHotspotsParams
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TextRangeWithHashDto
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams
import org.sonarsource.sonarlint.core.clientapi.common.TokenDto
import org.sonarsource.sonarlint.core.clientapi.common.UsernamePasswordDto
import org.sonarsource.sonarlint.core.http.HttpClient
import org.sonarsource.sonarlint.core.serverconnection.IssueStorePaths
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse as CheckIssueStatusChangePermittedResponse

@Service(Service.Level.APP)
class BackendService @NonInjectable constructor(private val backend: SonarLintBackend) : Disposable {
    constructor() : this(SonarLintBackendImpl(SonarLintIntelliJClient))

    private val initializedBackend: SonarLintBackend by lazy {
        migrateStoragePath()
        val serverConnections = ServerConnectionService.getInstance().getConnections()
        val sonarCloudConnections =
            serverConnections.filterIsInstance<SonarCloudConnection>().map { toSonarCloudBackendConnection(it) }
        val sonarQubeConnections =
            serverConnections.filterIsInstance<SonarQubeConnection>().map { toSonarQubeBackendConnection(it) }
        backend.initialize(
            InitializeParams(
                ClientInfoDto(
                    ApplicationInfo.getInstance().versionName,
                    TelemetryManagerProvider.TELEMETRY_PRODUCT_KEY,
                    "SonarLint IntelliJ " + getService(SonarLintPlugin::class.java).version
                ),
                FeatureFlagsDto(true, true, true, true, true, true),
                getLocalStoragePath(),
                SonarLintEngineFactory.getWorkDir(),
                EmbeddedPlugins.findEmbeddedPlugins(),
                EmbeddedPlugins.getEmbeddedPluginsForConnectedMode(),
                EmbeddedPlugins.enabledLanguagesInStandaloneMode,
                EmbeddedPlugins.enabledLanguagesInConnectedMode,
                sonarQubeConnections,
                sonarCloudConnections,
                null,
                mapOf(),
                getGlobalSettings().isFocusOnNewCode
            )
        ).thenRun {
            ApplicationManager.getApplication().messageBus.connect()
                .subscribe(ServerConnectionsListener.TOPIC, object : ServerConnectionsListener.Adapter() {
                    override fun afterChange(allConnections: List<ServerConnection>) {
                        connectionsUpdated(allConnections)
                    }

                    override fun credentialsChanged(changedConnections: List<ServerConnection>) {
                        notifyCredentialsChanged(changedConnections)
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
        val scConnections = serverConnections.filterIsInstance<SonarCloudConnection>().map { toSonarCloudBackendConnection(it) }
        val sqConnections = serverConnections.filterIsInstance<SonarQubeConnection>().map { toSonarQubeBackendConnection(it) }
        initializedBackend.connectionService.didUpdateConnections(DidUpdateConnectionsParams(sqConnections, scConnections))
    }

    private fun notifyCredentialsChanged(connections: List<ServerConnection>) {
        connections.forEach { initializedBackend.connectionService.didChangeCredentials(DidChangeCredentialsParams(it.name)) }
    }

    private fun toSonarQubeBackendConnection(connection: SonarQubeConnection): SonarQubeConnectionConfigurationDto {
        return SonarQubeConnectionConfigurationDto(
            connection.name,
            connection.hostUrl,
            connection.notificationsDisabled
        )
    }

    private fun toSonarCloudBackendConnection(connection: SonarCloudConnection): SonarCloudConnectionConfigurationDto {
        return SonarCloudConnectionConfigurationDto(
            connection.name,
            connection.organizationKey,
            connection.notificationsDisabled
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

    fun modulesAdded(project: Project, modules: List<Module>) {
        val projectBinding = getService(project, ProjectBindingManager::class.java).binding
        initializedBackend.configurationService.didAddConfigurationScopes(
            DidAddConfigurationScopesParams(
                modules.map { toConfigurationScope(it, projectBinding) }
            )
        )
    }

    private fun toConfigurationScope(module: Module, projectBinding: ProjectBinding?): ConfigurationScopeDto {
        val moduleProjectKey = getService(module, ModuleBindingManager::class.java).configuredProjectKey
        return ConfigurationScopeDto(moduleId(module), projectId(module.project), true, module.name,
                BindingConfigurationDto(projectBinding?.connectionName, projectBinding?.let { moduleProjectKey }, true))
    }

    fun moduleRemoved(module: Module) {
        initializedBackend.configurationService.didRemoveConfigurationScope(DidRemoveConfigurationScopeParams(moduleId(module)))
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

    fun getStandaloneRuleDetails(params: GetStandaloneRuleDescriptionParams): CompletableFuture<GetStandaloneRuleDescriptionResponse> {
        return initializedBackend.rulesService.getStandaloneRuleDetails(params)
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

    fun markAsResolved(module: Module, issueKey: String, newStatus: ResolutionStatus, isTaintVulnerability: Boolean): CompletableFuture<Void> {
        return initializedBackend.issueService.changeStatus(
            ChangeIssueStatusParams(
                moduleId(module),
                issueKey,
                newStatus,
                isTaintVulnerability
            )
        )
    }

    fun reopenIssue(module: Module, issueId: String, isTaintIssue: Boolean): CompletableFuture<ReopenIssueResponse> {
        return initializedBackend.issueService.reopenIssue(ReopenIssueParams(moduleId(module), issueId, isTaintIssue))
    }

    fun addCommentOnIssue(module: Module, issueKey: String, comment: String): CompletableFuture<Void> {
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

    fun checkSmartNotificationsSupported(connection: PartialConnection): CompletableFuture<CheckSmartNotificationsSupportedResponse> {
        val params: CheckSmartNotificationsSupportedParams = if (connection.sonarProduct == SonarProduct.SONARCLOUD) {
            CheckSmartNotificationsSupportedParams(TransientSonarCloudConnectionDto(connection.organizationKey, getCredentials(connection)))
        } else {
            CheckSmartNotificationsSupportedParams(TransientSonarQubeConnectionDto(connection.hostUrl, getCredentials(connection)))
        }
        return initializedBackend.connectionService.checkSmartNotificationsSupported(params)
    }

    fun validateConnection(connection: PartialConnection): CompletableFuture<ValidateConnectionResponse> {
        val params: ValidateConnectionParams = if (connection.sonarProduct == SonarProduct.SONARCLOUD) {
            ValidateConnectionParams(TransientSonarCloudConnectionDto(connection.organizationKey, getCredentials(connection)))
        } else {
            ValidateConnectionParams(TransientSonarQubeConnectionDto(connection.hostUrl, getCredentials(connection)))
        }
        return initializedBackend.connectionService.validateConnection(params)
    }

    fun listUserOrganizations(connection: PartialConnection): CompletableFuture<ListUserOrganizationsResponse> {
        val params = ListUserOrganizationsParams(getCredentials(connection))
        return initializedBackend.connectionService.listUserOrganizations(params)
    }

    fun getOrganization(connection: PartialConnection, organizationKey: String): CompletableFuture<GetOrganizationResponse> {
        val params = GetOrganizationParams(getCredentials(connection), organizationKey)
        return initializedBackend.connectionService.getOrganization(params)
    }

    private fun getCredentials(connection: PartialConnection): Either<TokenDto, UsernamePasswordDto> {
        return connection.credentials.token?.let { token -> Either.forLeft(TokenDto(token)) }
                ?: Either.forRight(UsernamePasswordDto(connection.credentials.login, connection.credentials.password))
    }

    fun trackWithServerIssues(
        module: Module,
        liveIssuesByFile: Map<VirtualFile, Collection<LiveIssue>>,
        shouldFetchIssuesFromServer: Boolean,
    ) {
        val binding = getService(module, ModuleBindingManager::class.java).binding ?: return
        val serverRelativePathByVirtualFile = getRelativePaths(module.project, liveIssuesByFile.keys)
            .mapValues { (_, ideRelativePath) -> IssueStorePaths.idePathToServerPath(binding, ideRelativePath) }
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap()
        val virtualFileByServerRelativePath = serverRelativePathByVirtualFile.map { Pair(it.value, it.key) }.toMap()
        val rawIssuesByRelativePath =
            liveIssuesByFile
                .filterKeys { file -> serverRelativePathByVirtualFile.containsKey(file) }
                .entries.associate { (file, issues) ->
                    serverRelativePathByVirtualFile[file]!! to issues.map {
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

        initializedBackend.issueTrackingService.trackWithServerIssues(
            TrackWithServerIssuesParams(
                moduleId(module),
                rawIssuesByRelativePath,
                shouldFetchIssuesFromServer
            )
        ).thenAccept { response ->
            response.issuesByServerRelativePath.forEach { (serverRelativePath, trackedIssues) ->
                val file = virtualFileByServerRelativePath[serverRelativePath] ?: return@forEach
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
            .get()
    }

    fun trackWithServerHotspots(
        module: Module,
        liveHotspotsByFile: Map<VirtualFile, Collection<LiveSecurityHotspot>>,
        shouldFetchHotspotsFromServer: Boolean,
    ) {
        val binding = getService(module, ModuleBindingManager::class.java).binding ?: return
        val serverRelativePathByVirtualFile = getRelativePaths(module.project, liveHotspotsByFile.keys)
            .mapValues { (_, ideRelativePath) -> IssueStorePaths.idePathToServerPath(binding, ideRelativePath) }
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap()
        val virtualFileByServerRelativePath = serverRelativePathByVirtualFile.map { Pair(it.value, it.key) }.toMap()
        val rawHotspotsByRelativePath =
            liveHotspotsByFile
                .filterKeys { file -> serverRelativePathByVirtualFile.containsKey(file) }
                .entries.associate { (file, hotspots) ->
                    serverRelativePathByVirtualFile[file]!! to hotspots.map {
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

        initializedBackend.securityHotspotMatchingService.matchWithServerSecurityHotspots(
            MatchWithServerSecurityHotspotsParams(
                moduleId(module),
                rawHotspotsByRelativePath,
                shouldFetchHotspotsFromServer
            )
        ).thenAccept { response ->
            response.securityHotspotsByServerRelativePath.forEach { (serverRelativePath, trackedHotspots) ->
                val file = virtualFileByServerRelativePath[serverRelativePath] ?: return@forEach
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
            .get()
    }

    private fun toTextRangeWithHashDto(project: Project, finding: LiveFinding): TextRangeWithHashDto? {
        val rangeMarker = finding.range ?: return null
        if (!rangeMarker.isValid) {
            return null
        }
        return ReadActionUtils.computeReadActionSafely(project) {
            val startLine = rangeMarker.document.getLineNumber(rangeMarker.startOffset)
            val startLineOffset = rangeMarker.startOffset - rangeMarker.document.getLineStartOffset(startLine)
            val endLine = rangeMarker.document.getLineNumber(rangeMarker.endOffset)
            val endLineOffset = rangeMarker.endOffset - rangeMarker.document.getLineStartOffset(endLine)
            TextRangeWithHashDto(startLine + 1, startLineOffset, endLine + 1, endLineOffset, finding.textRangeHashString)
        }
    }

    fun getNewCodePeriodText(project: Project): String {
        // simplification as we ignore module bindings
        return try {
            initializedBackend.newCodeService.getNewCodeDefinition(GetNewCodeDefinitionParams(projectId(project)))
                    .thenApply { response -> if (response.isSupported) response.description else "(unsupported new code definition)" }.get()
        } catch (e: Exception) {
            "(unknown code period)"
        }
    }

    companion object {
        fun projectId(project: Project) = project.projectFilePath ?: "DEFAULT_PROJECT"

        fun moduleId(module: Module): String {
            // there is no reliable unique identifier for modules, we store one in settings
            return getSettingsFor(module).uniqueId
        }

        fun findModule(configScopeId: String): Module? {
            return ProjectManager.getInstance().openProjects.firstNotNullOfOrNull { project ->
                ModuleManager.getInstance(project).modules.firstOrNull { module -> getSettingsFor(module).uniqueId == configScopeId }
            }
        }

        fun findProject(configScopeId: String): Project? {
            return ProjectManager.getInstance().openProjects.find { projectId(it) == configScopeId }
        }
    }

    override fun dispose() {
        initializedBackend.shutdown()
    }
}
