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

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.replaceService
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.refEq
import org.mockito.kotlin.timeout
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarsource.sonarlint.core.client.utils.IssueResolutionStatus
import org.sonarsource.sonarlint.core.rpc.client.Sloop
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.BindingRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.DidVcsRepositoryChangeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.branch.SonarProjectBranchRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.GetOrganizationParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityTrackingRpcService

class BackendServiceTests : AbstractSonarLintHeavyTests() {

    private lateinit var sloop: Sloop
    private lateinit var backend: SonarLintRpcServer
    private lateinit var backendConnectionService: ConnectionRpcService
    private lateinit var backendConfigurationService: ConfigurationRpcService
    private lateinit var backendIssueService: IssueRpcService
    private lateinit var backendBindingService: BindingRpcService
    private lateinit var backendHotspotService: HotspotRpcService
    private lateinit var backendBranchService: SonarProjectBranchRpcService
    private lateinit var service: BackendService

    override fun initApplication() {
        super.initApplication()

        globalSettings.serverConnections = listOf(
            ServerConnection.newBuilder().setName("id").setHostUrl("url").build(),
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
                .build()
        )

        backend = mock(SonarLintRpcServer::class.java)
        `when`(backend.initialize(any())).thenReturn(CompletableFuture.completedFuture(null))
        backendConnectionService = mock(ConnectionRpcService::class.java)
        backendConfigurationService = mock(ConfigurationRpcService::class.java)
        backendIssueService = mock(IssueRpcService::class.java)
        backendBindingService = mock(BindingRpcService::class.java)
        backendHotspotService = mock(HotspotRpcService::class.java)
        backendBranchService = mock(SonarProjectBranchRpcService::class.java)
        val taintService = mock(TaintVulnerabilityTrackingRpcService::class.java)
        `when`(taintService.listAll(any())).thenReturn(CompletableFuture.completedFuture(ListAllResponse(emptyList())))
        `when`(backend.connectionService).thenReturn(backendConnectionService)
        `when`(backend.configurationService).thenReturn(backendConfigurationService)
        `when`(backend.issueService).thenReturn(backendIssueService)
        `when`(backend.bindingService).thenReturn(backendBindingService)
        `when`(backend.hotspotService).thenReturn(backendHotspotService)
        `when`(backend.sonarProjectBranchService).thenReturn(backendBranchService)
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
        // Ignore previous events caused by HeavyTestFrameworkOpening a project
        reset(backendConfigurationService)
    }

    @Test
    fun test_initialize_with_existing_connections_when_starting() {
        val paramsCaptor = argumentCaptor<InitializeParams>()
        verify(backend, timeout(500)).initialize(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple("id", "url"))
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple("id", "org"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarqube_connection() {
        service.connectionsUpdated(listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").build()))

        val paramsCaptor = argumentCaptor<DidUpdateConnectionsParams>()
        verify(backendConnectionService, timeout(500)).didUpdateConnections(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple("id", "url"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarcloud_connection() {
        service.connectionsUpdated(listOf(ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
            .build()))

        val paramsCaptor = argumentCaptor<DidUpdateConnectionsParams>()
        verify(backendConnectionService, timeout(500)).didUpdateConnections(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple("id", "org"))
    }

    @Test
    fun test_notify_backend_when_opening_a_non_bound_project() {
        service.projectOpened(project)

        val paramsCaptor = argumentCaptor<DidAddConfigurationScopesParams>()
        await().during(500, TimeUnit.MILLISECONDS).untilAsserted {
            verify(backendConfigurationService, timeout(500).atLeastOnce()).didAddConfigurationScopes(paramsCaptor.capture())
        }
        assertThat(paramsCaptor.firstValue.addedScopes).extracting(
            "id",
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
        val connection = ServerConnection.newBuilder().setName("id").setHostUrl("url").build()
        globalSettings.serverConnections = listOf(connection)
        projectSettings.bindTo(connection, "key")

        service.projectOpened(project)

        val paramsCaptor = argumentCaptor<DidAddConfigurationScopesParams>()
        verify(backendConfigurationService, timeout(500).atLeastOnce()).didAddConfigurationScopes(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.addedScopes).extracting(
            "id",
            "name",
            "parentId",
            "bindable",
            "binding.connectionId",
            "binding.sonarProjectKey",
            "binding.bindingSuggestionDisabled"
        ).containsExactly(tuple(projectBackendId(project), project.name, null, true, "id", "key", false))
    }

    @Test
    fun test_notify_backend_when_closing_a_project() {
        val newProject = ProjectManagerEx.getInstanceEx().openProject(Path.of("test"), OpenProjectTask.build().asNewProject())!!
        service.projectOpened(newProject)

        ProjectManagerEx.getInstanceEx().closeAndDispose(newProject)

        val paramsCaptor = argumentCaptor<DidRemoveConfigurationScopeParams>()
        verify(backendConfigurationService, timeout(500)).didRemoveConfigurationScope(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.removedId).isEqualTo(projectBackendId(newProject))
    }

    @Test
    fun test_notify_backend_when_binding_a_project() {
        service.projectBound(project, ProjectBinding("id", "key", emptyMap()))

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), "id", "key", false)
    }

    @Test
    fun test_notify_backend_when_binding_a_project_with_binding_suggestions_disabled() {
        projectSettings.isBindingSuggestionsEnabled = false
        service.projectBound(project, ProjectBinding("id", "key", emptyMap()))

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), "id", "key", true)
    }

    @Test
    fun test_notify_backend_when_binding_a_project_having_module_overrides() {
        projectSettings.isBindingSuggestionsEnabled = false
        val moduleBackendId = moduleBackendId(module)

        service.projectBound(project, ProjectBinding("id", "key", mapOf(Pair(module, "moduleKey"))))

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService, timeout(500).times(2)).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.allValues).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactlyInAnyOrder(
            tuple(projectBackendId(project), "id", "key", true),
            tuple(moduleBackendId, "id", "moduleKey", true)
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
        val previousSettings = SonarLintGlobalSettings()
        previousSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").setToken("oldToken").build())
        val newSettings = SonarLintGlobalSettings()
        newSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").setToken("newToken").build())

        ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(previousSettings, newSettings)

        await().atMost(Duration.ofSeconds(3)).untilAsserted {
            verify(backendConnectionService).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
        }
    }

    @Test
    fun test_notify_backend_when_connection_password_changed() {
        val previousSettings = SonarLintGlobalSettings()
        previousSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").setLogin("login").setPassword("oldPass").build())
        val newSettings = SonarLintGlobalSettings()
        newSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").setLogin("login").setPassword("newPass").build())

        ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(previousSettings, newSettings)

        await().atMost(Duration.ofSeconds(3)).untilAsserted {
            verify(backendConnectionService).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
        }
    }

    @Test
    fun test_notify_backend_when_connection_login_changed() {
        val previousSettings = SonarLintGlobalSettings()
        previousSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").setLogin("oldLogin").setPassword("pass").build())
        val newSettings = SonarLintGlobalSettings()
        newSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").setLogin("newLogin").setPassword("pass").build())

        ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(previousSettings, newSettings)

        await().atMost(Duration.ofSeconds(3)).untilAsserted {
            verify(backendConnectionService).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
        }
    }

    @Test
    fun test_do_not_notify_backend_of_credentials_change_when_connection_is_new() {
        val previousSettings = SonarLintGlobalSettings()
        previousSettings.serverConnections = emptyList()
        val newSettings = SonarLintGlobalSettings()
        newSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").setLogin("login").setPassword("newPass").build())

        ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(previousSettings, newSettings)

        verify(backendConnectionService, timeout(500).times(0)).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
    }

    @Test
    fun test_do_not_notify_backend_of_credentials_change_when_something_else_changed() {
        val previousSettings = SonarLintGlobalSettings()
        previousSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("oldUrl").setToken("token").build())
        val newSettings = SonarLintGlobalSettings()
        newSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("id").setHostUrl("newUrl").setToken("token").build())

        ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(previousSettings, newSettings)

        verify(backendConnectionService, timeout(500).times(0)).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
    }

    @Test
    fun test_shut_backend_down_when_disposing_service() {
        service.dispose()

        verify(backend, timeout(500)).shutdown()
    }

    @Test
    fun test_get_issue_details() {
        val issueId = UUID.randomUUID()

        service.getIssueDetails(module, issueId)

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
        val issueId = "id"
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
        val connectionId = "id"
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
        val connectionId = "id"
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
    fun test_check_sonarqube_smart_notification_supported() {
        val serverConnection = ServerConnection.newBuilder().setName("id").setHostUrl("url").build()

        service.checkSmartNotificationsSupported(serverConnection)

        val paramsCaptor = argumentCaptor<CheckSmartNotificationsSupportedParams>()
        verify(backendConnectionService, timeout(500)).checkSmartNotificationsSupported(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.transientConnection.isLeft).isTrue()
        assertThat(paramsCaptor.firstValue.transientConnection.left.serverUrl).isEqualTo("url")
    }

    @Test
    fun test_check_sonarcloud_smart_notification_supported() {
        val serverConnection =
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org").build()

        service.checkSmartNotificationsSupported(serverConnection)

        val paramsCaptor = argumentCaptor<CheckSmartNotificationsSupportedParams>()
        verify(backendConnectionService, timeout(500)).checkSmartNotificationsSupported(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.transientConnection.isRight).isTrue()
        assertThat(paramsCaptor.firstValue.transientConnection.right.organization).isEqualTo("org")
    }

    @Test
    fun test_check_list_user_organizations_for_sonarqube() {
        val serverConnection = ServerConnection.newBuilder().setName("id").setHostUrl("url").build()

        service.listUserOrganizations(serverConnection)

        val paramsCaptor = argumentCaptor<ListUserOrganizationsParams>()
        verify(backendConnectionService, timeout(500)).listUserOrganizations(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.credentials.isRight).isTrue()
    }

    @Test
    fun test_check_list_user_organizations_for_sonarcloud() {
        val serverConnection =
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org").build()

        service.listUserOrganizations(serverConnection)

        val paramsCaptor = argumentCaptor<ListUserOrganizationsParams>()
        verify(backendConnectionService, timeout(500)).listUserOrganizations(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.credentials.isRight).isTrue()
    }

    @Test
    fun test_get_organization() {
        val serverConnection =
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org").build()
        val organizationKey = "org"

        service.getOrganization(serverConnection, organizationKey)

        val paramsCaptor = argumentCaptor<GetOrganizationParams>()
        verify(backendConnectionService, timeout(500)).getOrganization(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.organizationKey).isEqualTo(organizationKey)
        assertThat(paramsCaptor.firstValue.credentials.isRight).isTrue()
    }

}
