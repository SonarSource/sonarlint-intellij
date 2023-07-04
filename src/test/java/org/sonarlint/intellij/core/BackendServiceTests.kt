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
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.ConfigurationService
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.clientapi.backend.connection.ConnectionService
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.DidUpdateConnectionsParams
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class BackendServiceTests : AbstractSonarLintHeavyTests() {

    private lateinit var backend: SonarLintBackend
    private lateinit var backendConnectionService: ConnectionService
    private lateinit var backendConfigurationService: ConfigurationService
    private lateinit var service: BackendService

    override fun initApplication() {
        super.initApplication()

        globalSettings.serverConnections = listOf(
            ServerConnection.newBuilder().setName("id").setHostUrl("url").build(),
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
                .build()
        )

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
            .containsExactly(tuple("id", "url"))
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple("id", "org"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarqube_connection() {
        service.connectionsUpdated(listOf(ServerConnection.newBuilder().setName("id").setHostUrl("url").build()))

        val paramsCaptor = argumentCaptor<DidUpdateConnectionsParams>()
        verify(backendConnectionService).didUpdateConnections(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple("id", "url"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarcloud_connection() {
        service.connectionsUpdated(listOf(ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
            .build()))

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
        val connection = ServerConnection.newBuilder().setName("id").setHostUrl("url").build()
        globalSettings.serverConnections = listOf(connection)
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
        val newProject = ProjectManagerEx.getInstanceEx().openProject(Path.of("test"), OpenProjectTask.newProject())!!
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
    fun test_shut_backend_down_when_disposing_service() {
        service.dispose()

        verify(backend).shutdown()
    }
}
