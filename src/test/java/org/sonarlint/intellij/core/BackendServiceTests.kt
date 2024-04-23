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
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
import org.sonarsource.sonarlint.core.rpc.client.Sloop
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.ConfigurationRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidChangeCredentialsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityTrackingRpcService

class BackendServiceTests : AbstractSonarLintHeavyTests() {

    private lateinit var sloop: Sloop
    private lateinit var backend: SonarLintRpcServer
    private lateinit var backendConnectionService: ConnectionRpcService
    private lateinit var backendConfigurationService: ConfigurationRpcService
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
        val taintService = mock(TaintVulnerabilityTrackingRpcService::class.java)
        `when`(taintService.listAll(any())).thenReturn(CompletableFuture.completedFuture(ListAllResponse(emptyList())))
        `when`(backend.connectionService).thenReturn(backendConnectionService)
        `when`(backend.configurationService).thenReturn(backendConfigurationService)
        `when`(backend.taintVulnerabilityTrackingService).thenReturn(taintService)
        sloop = mock(Sloop::class.java)
        `when`(sloop.rpcServer).thenReturn(backend)
        `when`(sloop.onExit()).thenReturn(CompletableFuture.completedFuture(null))
        val sloopLauncher = mock(SloopLauncher::class.java)
        `when`(sloopLauncher.start(any(), any())).thenReturn(sloop)
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
        verify(backendConfigurationService, timeout(500)).didAddConfigurationScopes(paramsCaptor.capture())
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
        verify(backendConfigurationService, timeout(500)).didAddConfigurationScopes(paramsCaptor.capture())
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
        ).containsExactly(
            tuple(projectBackendId(project), "id", "key", true),
            tuple(moduleBackendId, "id", "moduleKey", true)
        )
    }

    @Test
    // TODO re-enable
    @Disabled
    fun test_notify_backend_when_closing_a_project_having_module_overrides() {
        projectSettings.isBindingSuggestionsEnabled = false
        val moduleId = moduleBackendId(module)

        service.projectClosed(project)

        val paramsCaptor = argumentCaptor<DidRemoveConfigurationScopeParams>()
        verify(backendConfigurationService, timeout(500).times(2)).didRemoveConfigurationScope(paramsCaptor.capture())
        assertThat(paramsCaptor.allValues).extracting(
            "removedId"
        ).containsExactly(
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

        verify(backend).shutdown()
    }
}
