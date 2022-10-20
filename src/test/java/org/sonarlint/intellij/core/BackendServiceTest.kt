/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import com.intellij.openapi.project.ex.ProjectManagerEx
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.sonarlint.intellij.AbstractSonarLintHeavyTest
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend
import org.sonarsource.sonarlint.core.clientapi.config.ConfigurationService
import org.sonarsource.sonarlint.core.clientapi.config.binding.DidUpdateBindingParams
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopesParams
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidRemoveConfigurationScopeParams
import org.sonarsource.sonarlint.core.clientapi.connection.ConnectionService
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidAddConnectionParams
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidRemoveConnectionParams
import org.sonarsource.sonarlint.core.clientapi.connection.config.DidUpdateConnectionParams
import org.sonarsource.sonarlint.core.clientapi.connection.config.InitializeParams
import java.nio.file.Path

class BackendServiceTest : AbstractSonarLintHeavyTest() {
    private lateinit var backend: SonarLintBackend
    private lateinit var backendConnectionService: ConnectionService
    private lateinit var backendConfigurationService: ConfigurationService
    private lateinit var service: BackendService

    override fun setUp() {
        super.setUp()
        backend = mock(SonarLintBackend::class.java)
        backendConnectionService = mock(ConnectionService::class.java)
        backendConfigurationService = mock(ConfigurationService::class.java)
        `when`(backend.connectionService).thenReturn(backendConnectionService)
        `when`(backend.configurationService).thenReturn(backendConfigurationService)
        service = BackendService(backend)
    }

    @Test
    fun test_initialize_connection_service_with_existing_connections_when_starting() {
        globalSettings.serverConnections = listOf(
            ServerConnection.newBuilder().setName("id").setHostUrl("url").build(),
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
                .build()
        )

        service.startOnce()

        val paramsCaptor = argumentCaptor<InitializeParams>()
        verify(backendConnectionService).initialize(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.sonarQubeConnections).extracting("connectionId", "serverUrl")
            .containsExactly(tuple("id", "url"))
        assertThat(paramsCaptor.firstValue.sonarCloudConnections).extracting("connectionId", "organization")
            .containsExactly(tuple("id", "org"))
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarqube_connection() {
        service.connectionAdded(ServerConnection.newBuilder().setName("id").setHostUrl("url").build())

        val paramsCaptor = argumentCaptor<DidAddConnectionParams>()
        verify(backendConnectionService).didAddConnection(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.addedConnection.left).extracting("connectionId", "serverUrl")
            .containsExactly("id", "url")
    }

    @Test
    fun test_notify_backend_when_adding_a_sonarcloud_connection() {
        service.connectionAdded(
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
                .build()
        )

        val paramsCaptor = argumentCaptor<DidAddConnectionParams>()
        verify(backendConnectionService).didAddConnection(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.addedConnection.right).extracting("connectionId", "organization")
            .containsExactly("id", "org")
    }

    @Test
    fun test_notify_backend_when_updating_a_sonarqube_connection() {
        service.connectionUpdated(ServerConnection.newBuilder().setName("id").setHostUrl("url").build())

        val paramsCaptor = argumentCaptor<DidUpdateConnectionParams>()
        verify(backendConnectionService).didUpdateConnection(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.updatedConnection.left).extracting("connectionId", "serverUrl")
            .containsExactly("id", "url")
    }

    @Test
    fun test_notify_backend_when_updating_a_sonarcloud_connection() {
        service.connectionUpdated(
            ServerConnection.newBuilder().setName("id").setHostUrl("https://sonarcloud.io").setOrganizationKey("org")
                .build()
        )

        val paramsCaptor = argumentCaptor<DidUpdateConnectionParams>()
        verify(backendConnectionService).didUpdateConnection(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.updatedConnection.right).extracting("connectionId", "organization")
            .containsExactly("id", "org")
    }

    @Test
    fun test_notify_backend_when_removing_a_connection() {
        service.connectionRemoved("id")

        val paramsCaptor = argumentCaptor<DidRemoveConnectionParams>()
        verify(backendConnectionService).didRemoveConnection(paramsCaptor.capture())
        assertThat(paramsCaptor.firstValue.connectionId).isEqualTo("id")
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
