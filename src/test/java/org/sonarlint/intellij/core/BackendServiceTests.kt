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

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.replaceService
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.refEq
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.ServerConnectionCredentials
import org.sonarlint.intellij.config.global.ServerConnectionService
import org.sonarlint.intellij.config.global.ServerConnectionWithAuth
import org.sonarlint.intellij.fixtures.newSonarCloudConnection
import org.sonarlint.intellij.fixtures.newSonarQubeConnection
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend
import org.sonarsource.sonarlint.core.clientapi.backend.config.ConfigurationService
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.ConnectionService
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidChangeCredentialsParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams

class BackendServiceTests : AbstractSonarLintHeavyTests() {

    private lateinit var backend: SonarLintBackend
    private lateinit var backendConnectionService: ConnectionService
    private lateinit var backendConfigurationService: ConfigurationService
    private lateinit var service: BackendService

    override fun initApplication() {
        super.initApplication()

        clearServerConnections()
        setServerConnections(newSonarQubeConnection("idSQ", "url"), newSonarCloudConnection("idSC", "org"))

        backend = mock(SonarLintBackend::class.java)
        `when`(backend.initialize(any())).thenReturn(CompletableFuture.completedFuture(null))
        backendConnectionService = mock(ConnectionService::class.java)
        backendConfigurationService = mock(ConfigurationService::class.java)
        `when`(backend.connectionService).thenReturn(backendConnectionService)
        `when`(backend.configurationService).thenReturn(backendConfigurationService)
        service = BackendService(backend)
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
        verify(backend).initialize(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple("idSQ", "url"))
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple("idSC", "org"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarqube_connection() {
        reset(backendConnectionService)
        service.connectionsUpdated(listOf(newSonarQubeConnection("id", "url")))

        val paramsCaptor = argumentCaptor<DidUpdateConnectionsParams>()
        verify(backendConnectionService).didUpdateConnections(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple("id", "url"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarcloud_connection() {
        reset(backendConnectionService)
        service.connectionsUpdated(listOf(newSonarCloudConnection("id", "org")))

        val paramsCaptor = argumentCaptor<DidUpdateConnectionsParams>()
        verify(backendConnectionService).didUpdateConnections(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple("id", "org"))
    }

    @Test
    fun test_notify_backend_when_opening_a_non_bound_project() {
        service.projectOpened(project)

        val paramsCaptor = argumentCaptor<DidAddConfigurationScopesParams>()
        verify(backendConfigurationService).didAddConfigurationScopes(paramsCaptor.capture())
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
        val connection = newSonarQubeConnection("id", "url")
        setServerConnections(connection)
        projectSettings.bindTo(connection, "key")

        service.projectOpened(project)

        val paramsCaptor = argumentCaptor<DidAddConfigurationScopesParams>()
        verify(backendConfigurationService).didAddConfigurationScopes(paramsCaptor.capture())
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
        verify(backendConfigurationService).didRemoveConfigurationScope(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.removedId).isEqualTo(projectBackendId(newProject))
    }

    @Test
    fun test_notify_backend_when_binding_a_project() {
        service.projectBound(project, ProjectBinding("id", "key", emptyMap()))

        val paramsCaptor = argumentCaptor<DidUpdateBindingParams>()
        verify(backendConfigurationService).didUpdateBinding(paramsCaptor.capture())
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
        verify(backendConfigurationService).didUpdateBinding(paramsCaptor.capture())
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
        verify(backendConfigurationService, times(2)).didUpdateBinding(paramsCaptor.capture())
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
    fun test_notify_backend_when_closing_a_project_having_module_overrides() {
        projectSettings.isBindingSuggestionsEnabled = false
        val moduleId = moduleBackendId(module)

        service.projectClosed(project)

        val paramsCaptor = argumentCaptor<DidRemoveConfigurationScopeParams>()
        verify(backendConfigurationService, times(2)).didRemoveConfigurationScope(paramsCaptor.capture())
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
        verify(backendConfigurationService).didUpdateBinding(paramsCaptor.capture())
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
        verify(backendConfigurationService).didUpdateBinding(paramsCaptor.capture())
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
        verify(backendConfigurationService).didUpdateBinding(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue).extracting(
            "configScopeId",
            "updatedBinding.connectionId",
            "updatedBinding.sonarProjectKey",
            "updatedBinding.bindingSuggestionDisabled"
        ).containsExactly(projectBackendId(project), null, null, true)
    }

    @Test
    fun test_notify_backend_when_connection_token_changed() {
        reset(backendConnectionService)
        updateServerCredentials("idSQ", credentials = ServerConnectionCredentials(null, null, "oldToken"))

        verify(backendConnectionService).didChangeCredentials(refEq(DidChangeCredentialsParams("idSQ")))
    }

    @Test
    fun test_notify_backend_when_connection_password_changed() {
        addServerConnectionsWithAuth(newSonarQubeConnection("id"), credentials = ServerConnectionCredentials("login", "oldPass", null))
        reset(backendConnectionService)
        updateServerCredentials("id", credentials = ServerConnectionCredentials("login", "newPass", null))

        verify(backendConnectionService).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
    }

    @Test
    fun test_notify_backend_when_connection_login_changed() {
        addServerConnectionsWithAuth(newSonarQubeConnection("id"), credentials = ServerConnectionCredentials("oldLogin", "pass", null))
        reset(backendConnectionService)
        updateServerCredentials("id", credentials = ServerConnectionCredentials("newLogin", "pass", null))

        verify(backendConnectionService).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
    }

    @Test
    fun test_do_not_notify_backend_of_credentials_change_when_connection_is_new() {
        clearServerConnections()
        reset(backendConnectionService)
        addServerConnectionsWithAuth(newSonarQubeConnection("id"), credentials = ServerConnectionCredentials("login", "newPass", null))

        verify(backendConnectionService, never()).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
    }

    @Test
    fun test_do_not_notify_backend_of_credentials_change_when_something_else_changed() {
        setServerConnections(newSonarQubeConnection("id", "oldUrl"))
        reset(backendConnectionService)
        setServerConnections(newSonarQubeConnection("id", "newUrl"))

        verify(backendConnectionService, never()).didChangeCredentials(refEq(DidChangeCredentialsParams("id")))
    }

    @Test
    fun test_shut_backend_down_when_disposing_service() {
        service.dispose()

        verify(backend).shutdown()
    }

    private fun addServerConnectionsWithAuth(connection: ServerConnection, credentials: ServerConnectionCredentials) {
        addServerConnectionsWithAuth(listOf(ServerConnectionWithAuth(connection, credentials)))
    }

    private fun updateServerCredentials(connectionName: String, credentials: ServerConnectionCredentials) {
        val connection = ServerConnectionService.getInstance().getConnections().find { it.name == connectionName }!!
        ServerConnectionService.getInstance().updateServerConnections(globalSettings, emptySet(), listOf(ServerConnectionWithAuth(connection, credentials)), emptyList())
    }

    private fun setServerConnections(vararg connections: ServerConnection) {
        addServerConnectionsWithAuth(connections.map { ServerConnectionWithAuth(it, ServerConnectionCredentials(null, null, "token")) })
    }

    private fun addServerConnectionsWithAuth(connections: List<ServerConnectionWithAuth>) {
        ServerConnectionService.getInstance().updateServerConnections(globalSettings, emptySet(), emptyList(), connections)
    }

    private fun clearServerConnections() {
        ServerConnectionService.getInstance().updateServerConnections(globalSettings, ServerConnectionService.getInstance().getConnections().map { it.name }.toSet(), emptyList(), emptyList())
    }
}
