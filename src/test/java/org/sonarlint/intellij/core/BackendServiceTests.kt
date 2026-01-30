/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.replaceService
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.refEq
import org.mockito.kotlin.timeout
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.credentials.eraseToken
import org.sonarlint.intellij.config.global.credentials.eraseUsernamePassword
import org.sonarlint.intellij.config.global.credentials.setToken
import org.sonarlint.intellij.messages.CredentialsChangeListener
import org.sonarsource.sonarlint.core.client.utils.IssueResolutionStatus
import org.sonarsource.sonarlint.core.rpc.client.Sloop
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.AddIssueCommentParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ChangeIssueStatusParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.IssueRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ReopenIssueParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RulesRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityTrackingRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AcceptedBindingSuggestionParams
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion

private const val CONNECTION_NAME = "id"

class BackendServiceTests : AbstractSonarLintHeavyTests() {

    private lateinit var sloop: Sloop
    private lateinit var backend: SonarLintRpcServer
    private lateinit var backendConnectionService: ConnectionRpcService
    private lateinit var backendConfigurationService: ConfigurationRpcService
    private lateinit var backendRuleService: RulesRpcService
    private lateinit var backendIssueService: IssueRpcService
    private lateinit var backendBindingService: BindingRpcService
    private lateinit var backendHotspotService: HotspotRpcService
    private lateinit var backendBranchService: SonarProjectBranchRpcService
    private lateinit var backendFileService: FileRpcService
    private lateinit var backendTelemetryService: TelemetryRpcService
    private lateinit var service: BackendService
    private var previousTelemetryDisabledValue: String = System.getProperty("sonarlint.telemetry.disabled")
    private var previousMonitoringDisabledValue: String = System.getProperty("sonarlint.monitoring.disabled")

    override fun initApplication() {
        super.initApplication()

        globalSettings.serverConnections = listOf(
            ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl("url").build(),
            ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
                .build()
        )

        backend = mock(SonarLintRpcServer::class.java)
        `when`(backend.initialize(any())).thenReturn(CompletableFuture.completedFuture(null))
        backendConnectionService = mock(ConnectionRpcService::class.java)
        backendConfigurationService = mock(ConfigurationRpcService::class.java)
        backendRuleService = mock(RulesRpcService::class.java)
        backendIssueService = mock(IssueRpcService::class.java)
        backendBindingService = mock(BindingRpcService::class.java)
        backendHotspotService = mock(HotspotRpcService::class.java)
        backendBranchService = mock(SonarProjectBranchRpcService::class.java)
        backendFileService = mock(FileRpcService::class.java)
        backendTelemetryService = mock(TelemetryRpcService::class.java)
        val taintService = mock(TaintVulnerabilityTrackingRpcService::class.java)
        `when`(taintService.listAll(any())).thenReturn(CompletableFuture.completedFuture(ListAllResponse(emptyList())))
        `when`(backend.fileService).thenReturn(backendFileService)
        `when`(backend.connectionService).thenReturn(backendConnectionService)
        `when`(backend.configurationService).thenReturn(backendConfigurationService)
        `when`(backend.rulesService).thenReturn(backendRuleService)
        `when`(backend.issueService).thenReturn(backendIssueService)
        `when`(backend.bindingService).thenReturn(backendBindingService)
        `when`(backend.hotspotService).thenReturn(backendHotspotService)
        `when`(backend.sonarProjectBranchService).thenReturn(backendBranchService)
        `when`(backend.telemetryService).thenReturn(backendTelemetryService)
        `when`(backend.taintVulnerabilityTrackingService).thenReturn(taintService)
        sloop = mock(Sloop::class.java)
        `when`(sloop.rpcServer).thenReturn(backend)
        `when`(sloop.onExit()).thenReturn(CompletableFuture.completedFuture(null))
        val sloopLauncher = mock(SloopLauncher::class.java)
        `when`(sloopLauncher.start(any(), any(), any())).thenReturn(sloop)
        service = BackendService(sloopLauncher)
        ApplicationManager.getApplication().replaceService(BackendService::class.java, service, testRootDisposable)
    }

    @BeforeEach
    fun resetMockBackend() {
        // Trigger initialization on the mock-based service (since ModuleChangeListener skips in test mode)
        service.modulesAdded(project, listOf())

        // Wait for async initialization and configuration scope setup to complete before resetting mocks
        verify(backend, timeout(2000)).initialize(any())
        verify(backendConfigurationService, timeout(2000)).didAddConfigurationScopes(any())

        // Ignore previous events caused by HeavyTestFrameworkOpening a project
        reset(backendConfigurationService)

        previousTelemetryDisabledValue = System.getProperty("sonarlint.telemetry.disabled")
        previousMonitoringDisabledValue = System.getProperty("sonarlint.monitoring.disabled")
    }

    @AfterEach
    override fun tearDown() {
        PasswordSafe.instance.eraseToken(CONNECTION_NAME)
        PasswordSafe.instance.eraseUsernamePassword(CONNECTION_NAME)

        System.setProperty("sonarlint.telemetry.disabled", previousTelemetryDisabledValue)
        System.setProperty("sonarlint.monitoring.disabled", previousMonitoringDisabledValue)
    }

    @Test
    fun test_initialize_with_existing_connections_when_starting() {
        val paramsCaptor = argumentCaptor<InitializeParams>()
        verify(backend, timeout(500)).initialize(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple(CONNECTION_NAME, "url"))
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple(CONNECTION_NAME, "org"))
    }

    @Test
    fun test_initialize_params_focus_and_auto_analysis() {
        verify(backend, timeout(2000)).initialize(any())
        clearInvocations(backend)
        globalSettings.isFocusOnNewCode = true
        globalSettings.isAutoTrigger = false
        service.restartBackendService()
        val paramsCaptor = argumentCaptor<InitializeParams>()

        verify(backend, timeout(2000)).initialize(paramsCaptor.capture())

        val params = paramsCaptor.firstValue
        assertThat(params.isFocusOnNewCode).isTrue()
        assertThat(params.isAutomaticAnalysisEnabled).isFalse()
    }

    @Test
    fun test_initialize_params_http_configuration() {
        try {
            verify(backend, timeout(2000)).initialize(any())
            clearInvocations(backend)
            System.setProperty("sonarlint.ssl.trustStorePath", "/tmp/truststore")
            System.setProperty("sonarlint.ssl.trustStorePassword", "trustpass")
            System.setProperty("sonarlint.ssl.trustStoreType", "JKS")
            System.setProperty("sonarlint.ssl.keyStorePath", "/tmp/keystore")
            System.setProperty("sonarlint.ssl.keyStorePassword", "keypass")
            System.setProperty("sonarlint.ssl.keyStoreType", "PKCS12")
            System.setProperty("sonarlint.http.connectTimeout", "5")
            System.setProperty("sonarlint.http.socketTimeout", "PT10S")
            System.setProperty("sonarlint.http.connectionRequestTimeout", "PT15S")
            System.setProperty("sonarlint.http.responseTimeout", "PT20S")
            service.restartBackendService()
            val paramsCaptor = argumentCaptor<InitializeParams>()
            verify(backend, timeout(2000)).initialize(paramsCaptor.capture())
            val httpConfig = paramsCaptor.firstValue.httpConfiguration
            val trustStorePath = httpConfig.sslConfiguration.trustStorePath?.toString()?.replace('\\', '/')
            val keyStorePath = httpConfig.sslConfiguration.keyStorePath?.toString()?.replace('\\', '/')
            assertThat(trustStorePath).endsWith("/tmp/truststore")
            assertThat(httpConfig.sslConfiguration.trustStorePassword).isEqualTo("trustpass")
            assertThat(httpConfig.sslConfiguration.trustStoreType).isEqualTo("JKS")
            assertThat(keyStorePath).endsWith("/tmp/keystore")
            assertThat(httpConfig.sslConfiguration.keyStorePassword).isEqualTo("keypass")
            assertThat(httpConfig.sslConfiguration.keyStoreType).isEqualTo("PKCS12")
            assertThat(httpConfig.connectTimeout).isEqualTo(Duration.ofMinutes(5))
            assertThat(httpConfig.socketTimeout).isEqualTo(Duration.parse("PT10S"))
            assertThat(httpConfig.connectionRequestTimeout).isEqualTo(Duration.parse("PT15S"))
            assertThat(httpConfig.responseTimeout).isEqualTo(Duration.parse("PT20S"))
        } finally {
            System.clearProperty("sonarlint.ssl.trustStorePath")
            System.clearProperty("sonarlint.ssl.trustStorePassword")
            System.clearProperty("sonarlint.ssl.trustStoreType")
            System.clearProperty("sonarlint.ssl.keyStorePath")
            System.clearProperty("sonarlint.ssl.keyStorePassword")
            System.clearProperty("sonarlint.ssl.keyStoreType")
            System.clearProperty("sonarlint.http.connectTimeout")
            System.clearProperty("sonarlint.http.socketTimeout")
            System.clearProperty("sonarlint.http.connectionRequestTimeout")
            System.clearProperty("sonarlint.http.responseTimeout")
        }
    }

    @Test
    fun test_initialize_params_backend_capabilities() {
        clearInvocations(backend)
        System.setProperty("sonarlint.telemetry.disabled", "true")
        System.setProperty("sonarlint.monitoring.disabled", "true")
        service.restartBackendService()
        val paramsCaptor = argumentCaptor<InitializeParams>()

        verify(backend, timeout(2000)).initialize(paramsCaptor.capture())

        val params = paramsCaptor.firstValue
        assertThat(params.backendCapabilities).doesNotContain(
            org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.TELEMETRY,
            org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.MONITORING
        )
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarqube_connection() {
        service.connectionsUpdated(listOf(ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl("url").build()))

        val paramsCaptor = argumentCaptor<DidUpdateConnectionsParams>()
        verify(backendConnectionService, timeout(500)).didUpdateConnections(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple(CONNECTION_NAME, "url"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarcloud_connection() {
        service.connectionsUpdated(listOf(ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
            .build()))

        val paramsCaptor = argumentCaptor<DidUpdateConnectionsParams>()
        verify(backendConnectionService, timeout(500)).didUpdateConnections(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple(CONNECTION_NAME, "org"))
    }

    @Test
    fun test_notify_backend_when_opening_a_non_bound_project() {
        service.projectClosed(project)
        service.modulesAdded(project, listOf())

        val paramsCaptor = argumentCaptor<DidAddConfigurationScopesParams>()
        await().during(500, TimeUnit.MILLISECONDS).untilAsserted {
            verify(backendConfigurationService, timeout(500).atLeastOnce()).didAddConfigurationScopes(paramsCaptor.capture())
        }
        assertThat(paramsCaptor.firstValue.addedScopes).extracting(
            CONNECTION_NAME,
            "name",
            "parentId",
            "bindable",
            "binding.connectionId",
            "binding.sonarProjectKey",
            "binding.bindingSuggestionDisabled"
        ).containsExactly(tuple(projectBackendId(project), project.name, null, true, null, null, false))
    }

    @Test
    fun test_notify_backend_when_opening_a_bound_project() {
        val connection = ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl("url").build()
        globalSettings.serverConnections = listOf(connection)
        projectSettings.bindTo(connection, "key")

        service.projectClosed(project)
        service.modulesAdded(project, listOf())

        val paramsCaptor = argumentCaptor<DidAddConfigurationScopesParams>()
        verify(backendConfigurationService, timeout(500).atLeastOnce()).didAddConfigurationScopes(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.addedScopes).extracting(
            CONNECTION_NAME,
            "name",
            "parentId",
            "bindable",
            "binding.connectionId",
            "binding.sonarProjectKey",
            "binding.bindingSuggestionDisabled"
        ).containsExactly(tuple(projectBackendId(project), project.name, null, true, CONNECTION_NAME, "key", false))
    }

    @Test
    fun test_notify_backend_when_closing_a_project() {
        val newProject = ProjectManagerEx.getInstanceEx().openProject(Path.of("test"), OpenProjectTask.build().asNewProject())!!
        service.modulesAdded(newProject, listOf())

        ProjectManagerEx.getInstanceEx().closeAndDispose(newProject)

        val paramsCaptor = argumentCaptor<DidRemoveConfigurationScopeParams>()
        verify(backendConfigurationService, timeout(500)).didRemoveConfigurationScope(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.removedId).isEqualTo(projectBackendId(newProject))
    }

    @Test
    fun test_notify_backend_when_binding_a_project() {
        service.projectBound(project, ProjectBinding(CONNECTION_NAME, "key", emptyMap()), BindingSuggestionOrigin.PROJECT_NAME)

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), CONNECTION_NAME, "key", false)

        val acceptedSuggestionCaptor = argumentCaptor<AcceptedBindingSuggestionParams>()
        verify(backendTelemetryService).acceptedBindingSuggestion(acceptedSuggestionCaptor.capture())
        assertThat(acceptedSuggestionCaptor.firstValue).extracting(AcceptedBindingSuggestionParams::getOrigin)
            .isEqualTo(BindingSuggestionOrigin.PROJECT_NAME)
    }

    @Test
    fun test_notify_backend_when_binding_a_project_manually() {
        service.projectBound(project, ProjectBinding(CONNECTION_NAME, "key", emptyMap()), null)

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), CONNECTION_NAME, "key", false)
        verify(backendTelemetryService).addedManualBindings()
    }

    @Test
    fun test_notify_backend_when_binding_a_project_with_binding_suggestions_disabled() {
        projectSettings.isBindingSuggestionsEnabled = false
        service.projectBound(project, ProjectBinding(CONNECTION_NAME, "key", emptyMap()), BindingSuggestionOrigin.PROJECT_NAME)

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), CONNECTION_NAME, "key", true)
    }

    @Test
    fun test_notify_backend_when_binding_a_project_having_module_overrides() {
        projectSettings.isBindingSuggestionsEnabled = false
        val moduleBackendId = moduleBackendId(module)

        service.projectBound(project, ProjectBinding(CONNECTION_NAME, "key", mapOf(Pair(module, "moduleKey"))), BindingSuggestionOrigin.PROJECT_NAME)

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500).times(2)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.allValues).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactlyInAnyOrder(
            tuple(projectBackendId(project), CONNECTION_NAME, "key", true),
            tuple(moduleBackendId, CONNECTION_NAME, "moduleKey", true)
        )
    }

    @Test
    fun test_notify_backend_when_closing_a_project_having_module_overrides() {
        projectSettings.isBindingSuggestionsEnabled = false
        val moduleId = moduleBackendId(module)

        service.projectClosed(project)

        val paramsCaptor = argumentCaptor<DidRemoveConfigurationScopeParams>()
        verify(backendConfigurationService, timeout(2_000).times(2)).didRemoveConfigurationScope(paramsCaptor.capture())
        assertThat(paramsCaptor.allValues).extracting(
            "removedId"
        ).containsExactlyInAnyOrder(
            moduleId,
            projectBackendId(project)
        )
    }

    @Test
    fun test_notify_backend_when_unbinding_a_project() {
        service.projectUnbound(project)

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), null, null, false)
    }

    @Test
    fun test_notify_backend_when_unbinding_a_module() {
        service.moduleUnbound(module)
        val moduleId = moduleBackendId(module)

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(moduleId, null, null, true)
    }

    @Test
    fun test_notify_backend_when_disabling_binding_suggestions_for_a_project() {
        service.bindingSuggestionsDisabled(project)

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), null, null, true)
    }

    @Test
    fun test_notify_backend_when_connection_token_changed() {
        ApplicationManager.getApplication().messageBus.syncPublisher(CredentialsChangeListener.TOPIC).onCredentialsChanged(CONNECTION_NAME)

        await().atMost(Duration.ofSeconds(3)).untilAsserted {
            verify(backendConnectionService).didChangeCredentials(refEq(DidChangeCredentialsParams(CONNECTION_NAME)))
        }
    }

    @Test
    fun test_shut_backend_down_when_disposing_service() {
        service.dispose()

        verify(backend, timeout(500)).shutdown()
    }

    @Test
    fun test_get_effective_rule_details() {
        val ruleKey = "key"

        service.getEffectiveRuleDetails(module, ruleKey, null)

        val paramsCaptor = argumentCaptor<GetEffectiveRuleDetailsParams>()
        verify(backendRuleService, timeout(500)).getEffectiveRuleDetails(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configurationScopeId",
            "ruleKey"
        ).containsExactly(moduleBackendId(module), ruleKey)
    }

    @Test
    fun test_get_effective_issue_details() {
        val issueId = UUID.randomUUID()

        service.getEffectiveIssueDetails(module, issueId)

        val paramsCaptor = argumentCaptor<GetEffectiveIssueDetailsParams>()
        verify(backendIssueService, timeout(500)).getEffectiveIssueDetails(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configurationScopeId",
            "issueId"
        ).containsExactly(moduleBackendId(module), issueId)
    }

    @Test
    fun test_get_shared_connected_mode_config() {
        service.getSharedConnectedModeConfigFileContents(project)

        val paramsCaptor = argumentCaptor<GetSharedConnectedModeConfigFileParams>()
        verify(backendBindingService, timeout(500)).getSharedConnectedModeConfigFileContents(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.configScopeId).isEqualTo(projectBackendId(project))
    }

    @Test
    fun test_help_generate_user_token() {
        val serverUrl = "http://localhost:1234"

        service.helpGenerateUserToken(serverUrl)

        val paramsCaptor = argumentCaptor<HelpGenerateUserTokenParams>()
        verify(backendConnectionService, timeout(500)).helpGenerateUserToken(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "serverUrl",
            "utm.medium",
            "utm.source",
            "utm.content",
            "utm.term",
        ).containsExactly(
            serverUrl,
            "referral",
            "sq-ide-product-intellij",
            "create-new-connection-panel",
            "create-sqc-token"
        )
    }

    @Test
    fun test_open_hotspot_in_browser() {
        val hotspotKey = "key"

        service.openHotspotInBrowser(module, hotspotKey)

        val paramsCaptor = argumentCaptor<OpenHotspotInBrowserParams>()
        verify(backendHotspotService, timeout(500)).openHotspotInBrowser(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "hotspotKey"
        ).containsExactly(moduleBackendId(module), hotspotKey)
    }

    @Test
    fun test_check_local_detection_for_hotspot() {
        service.checkLocalSecurityHotspotDetectionSupported(project)

        val paramsCaptor = argumentCaptor<CheckLocalDetectionSupportedParams>()
        verify(backendHotspotService, timeout(500)).checkLocalDetectionSupported(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.configScopeId).isEqualTo(projectBackendId(project))
    }

    @Test
    fun test_change_status_for_hotspot() {
        val hotspotKey = "key"
        val newStatus = HotspotStatus.FIXED

        service.changeStatusForHotspot(module, hotspotKey, newStatus)

        val paramsCaptor = argumentCaptor<ChangeHotspotStatusParams>()
        verify(backendHotspotService, timeout(500)).changeStatus(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configurationScopeId",
            "hotspotKey",
            "newStatus"
        ).containsExactly(moduleBackendId(module), hotspotKey, newStatus)
    }

    @Test
    fun test_mark_as_resolved() {
        val issueKey = "key"
        val newStatus = IssueResolutionStatus.ACCEPT
        val isTaint = false

        service.markAsResolved(module, issueKey, newStatus, isTaint)

        val paramsCaptor = argumentCaptor<ChangeIssueStatusParams>()
        verify(backendIssueService, timeout(500)).changeStatus(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configurationScopeId",
            "issueKey",
            "newStatus",
            "isTaintIssue"
        ).containsExactly(moduleBackendId(module), issueKey, ResolutionStatus.valueOf(newStatus.name), isTaint)
    }

    @Test
    fun test_reopen_issue() {
        val issueId = CONNECTION_NAME
        val isTaint = false

        service.reopenIssue(module, issueId, isTaint)

        val paramsCaptor = argumentCaptor<ReopenIssueParams>()
        verify(backendIssueService, timeout(500)).reopenIssue(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configurationScopeId",
            "issueId",
            "isTaintIssue"
        ).containsExactly(moduleBackendId(module), issueId, isTaint)
    }

    @Test
    fun test_comment_issue() {
        val issueKey = "key"
        val comment = "comment"

        service.addCommentOnIssue(module, issueKey, comment)

        val paramsCaptor = argumentCaptor<AddIssueCommentParams>()
        verify(backendIssueService, timeout(500)).addComment(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configurationScopeId",
            "issueKey",
            "text"
        ).containsExactly(moduleBackendId(module), issueKey, comment)
    }

    @Test
    fun test_hotspot_change_permitted() {
        val connectionId = CONNECTION_NAME
        val hotspotKey = "key"

        service.checkStatusChangePermitted(connectionId, hotspotKey)

        val paramsCaptor = argumentCaptor<CheckStatusChangePermittedParams>()
        verify(backendHotspotService, timeout(500)).checkStatusChangePermitted(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "connectionId",
            "hotspotKey"
        ).containsExactly(connectionId, hotspotKey)
    }

    @Test
    fun test_issue_change_permitted() {
        val connectionId = CONNECTION_NAME
        val issueKey = "key"

        service.checkIssueStatusChangePermitted(connectionId, issueKey)

        val paramsCaptor = argumentCaptor<org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.CheckStatusChangePermittedParams>()
        verify(backendIssueService, timeout(500)).checkStatusChangePermitted(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "connectionId",
            "issueKey"
        ).containsExactly(connectionId, issueKey)
    }

    @Test
    fun test_did_vcs_repo_change() {
        service.didVcsRepoChange(project)

        val paramsCaptor = argumentCaptor<DidVcsRepositoryChangeParams>()
        verify(backendBranchService, timeout(500)).didVcsRepositoryChange(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.configurationScopeId).isEqualTo(projectBackendId(project))
    }

    @Test
    fun test_check_list_user_organizations_for_sonarqube() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, "token")
        val serverConnection = ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl("url").build()

        service.listUserOrganizations(serverConnection)

        val paramsCaptor = argumentCaptor<ListUserOrganizationsParams>()
        verify(backendConnectionService, timeout(500)).listUserOrganizations(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.region).isEqualTo(SonarCloudRegion.EU)
        assertThat(paramsCaptor.firstValue.credentials.isLeft).isTrue()
        assertThat(paramsCaptor.firstValue.credentials.left.token).isEqualTo("token")
    }

    @Test
    fun test_check_list_user_organizations_for_sonarcloud() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, "token")
        val serverConnection = ServerConnection.newBuilder()
            .setName(CONNECTION_NAME)
            .setRegion(SonarCloudRegion.US.name)
            .setHostUrl("https://sonarcloud.us")
            .setOrganizationKey("org")
            .build()

        service.listUserOrganizations(serverConnection)

        val paramsCaptor = argumentCaptor<ListUserOrganizationsParams>()
        verify(backendConnectionService, timeout(500)).listUserOrganizations(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.region).isEqualTo(SonarCloudRegion.US)
        assertThat(paramsCaptor.firstValue.credentials.isLeft).isTrue()
        assertThat(paramsCaptor.firstValue.credentials.left.token).isEqualTo("token")
    }

    @Test
    fun test_get_organization() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, "token")
        val serverConnection =
            ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl("https://sonarcloud.io").setOrganizationKey("org").build()
        val organizationKey = "org"

        service.getOrganization(serverConnection, organizationKey)

        val paramsCaptor = argumentCaptor<GetOrganizationParams>()
        verify(backendConnectionService, timeout(500)).getOrganization(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.organizationKey).isEqualTo(organizationKey)
        assertThat(paramsCaptor.firstValue.credentials.isLeft).isTrue()
        assertThat(paramsCaptor.firstValue.credentials.left.token).isEqualTo("token")
    }

    @Test
    fun test_did_open_file_for_project() {
        val file = mock<VirtualFile>()
        val fileSystem = mock<VirtualFileSystem>()

        `when`(fileSystem.protocol).thenReturn("file")
        `when`(file.fileSystem).thenReturn(fileSystem)
        `when`(file.path).thenReturn("/test.java")
        `when`(file.isInLocalFileSystem).thenReturn(true)

        service.didOpenFile(project, file)

        val paramsCaptor = argumentCaptor<DidOpenFileParams>()
        verify(backendFileService, timeout(500)).didOpenFile(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.configurationScopeId).isEqualTo(projectBackendId(project))
        assertThat(paramsCaptor.firstValue.fileUri.toString()).startsWith("file:/")
        assertThat(paramsCaptor.firstValue.fileUri.path).endsWith("/test.java")
    }

    @Test
    fun test_did_open_file_for_module() {
        val file = mock<VirtualFile>()
        val fileSystem = mock<VirtualFileSystem>()

        `when`(fileSystem.protocol).thenReturn("file")
        `when`(file.fileSystem).thenReturn(fileSystem)
        `when`(file.path).thenReturn("/test.java")
        `when`(file.isInLocalFileSystem).thenReturn(true)

        service.didOpenFile(module, file)

        val paramsCaptor = argumentCaptor<DidOpenFileParams>()
        verify(backendFileService, timeout(500)).didOpenFile(paramsCaptor.capture())

        assertThat(paramsCaptor.firstValue.configurationScopeId).isEqualTo(moduleBackendId(module))
        assertThat(paramsCaptor.firstValue.fileUri.scheme).isEqualTo("file")
        assertThat(paramsCaptor.firstValue.fileUri.path).endsWith("/test.java")
    }

}
