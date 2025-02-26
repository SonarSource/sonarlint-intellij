/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto


class SonarLintIntelliJClientTests : AbstractSonarLintLightTests() {
    lateinit var client: SonarLintIntelliJClient

    @BeforeEach
    fun prepare() {
        // also important as this starts the notification manager service
        clearNotifications()
        client = SonarLintIntelliJClient
    }

    @Test
    fun it_should_not_list_files_if_project_does_not_exist() {
        val result = client.listFiles("blah")

        assertThat(result).isEmpty()
    }

    @Test
    fun it_should_not_list_files_if_project_does_not_have_any() {
        val result = client.listFiles(projectBackendId)

        assertThat(result).isEmpty()
    }

    @Test
    fun it_should_find_files_if_project_has_one_with_matching_name() {
        myFixture.configureByFile("file.properties")

        val result = client.listFiles(projectBackendId)

        assertThat(result).extracting(ClientFileDto::getIdeRelativePath, ClientFileDto::getContent)
            .containsOnly(tuple(Paths.get("file.properties"), null))
    }

    @Test
    fun it_should_find_files_if_project_has_one_modified_in_editor_and_matching_name() {
        myFixture.configureByFile("file.properties")
        myFixture.type("pre")

        val result = client.listFiles(projectBackendId)

        assertThat(result).extracting(ClientFileDto::getIdeRelativePath, ClientFileDto::getContent)
            .containsOnly(tuple(Paths.get("file.properties"), null))
    }

    @Test
    fun it_should_find_files_with_content_if_specific_property_file() {
        myFixture.configureByFile("sonar-project.properties")

        val result = client.listFiles(projectBackendId)

        assertThat(result).extracting(ClientFileDto::getIdeRelativePath, ClientFileDto::getContent)
            .containsOnly(tuple(Paths.get("sonar-project.properties"), "content=hey\n"))
    }

    @Test
    fun it_should_find_files_with_content_if_specific_property_file_and_has_one_modified_in_editor() {
        myFixture.configureByFile("sonar-project.properties")
        myFixture.type("pre")

        val result = client.listFiles(projectBackendId)

        assertThat(result).extracting(ClientFileDto::getIdeRelativePath, ClientFileDto::getContent)
            .containsOnly(tuple(Paths.get("sonar-project.properties"), "precontent=hey\n"))
    }

    @Test
    fun it_should_suggest_exact_binding_if_there_is_one_suggestion() {
        globalSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("connectionId").build())

        client.suggestBinding(
            mapOf(
                projectBackendId to listOf(BindingSuggestionDto("connectionId", "projectKey", "projectName", false))
            )
        )

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "<b>SonarQube for IDE suggestions</b>", "Bind this project to 'projectName' on 'connectionId'?"
            )
        )
    }

    @Test
    fun it_should_suggest_binding_config_if_there_is_no_suggestion() {
        globalSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("connectionId").build())

        client.suggestBinding(mapOf(projectBackendId to emptyList()))

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "<b>SonarQube for IDE suggestions</b>",
                "Bind this project to SonarQube (Server, Cloud)?"
            )
        )
    }

    @Test
    fun it_should_suggest_binding_config_if_there_is_are_several_suggestions() {
        globalSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("connectionId").build())

        client.suggestBinding(
            mapOf(
                projectBackendId to listOf(
                    BindingSuggestionDto("connectionId", "projectKey", "projectName", false),
                    BindingSuggestionDto("connectionId", "projectKey2", "projectName2", false)
                )
            )
        )

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "<b>SonarQube for IDE suggestions</b>",
                "Bind this project to SonarQube (Server, Cloud)?"
            )
        )
    }

    @Test
    fun it_should_not_suggest_binding_if_the_project_is_unknown() {
        globalSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("connectionId").build())

        client.suggestBinding(
            mapOf(
                "wrongProjectId" to listOf(
                    BindingSuggestionDto("connectionId", "projectKey", "projectName", false),
                    BindingSuggestionDto("connectionId", "projectKey2", "projectName2", false)
                )
            )
        )

        assertThat(projectNotifications).isEmpty()
    }

    @Test
    fun it_should_not_suggest_binding_if_the_suggestions_are_disabled_by_user() {
        globalSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("connectionId").build())
        projectSettings.isBindingSuggestionsEnabled = false

        client.suggestBinding(
            mapOf(
                "wrongProjectId" to listOf(
                    BindingSuggestionDto("connectionId", "projectKey", "projectName", false),
                    BindingSuggestionDto("connectionId", "projectKey2", "projectName2", false)
                )
            )
        )

        assertThat(projectNotifications).isEmpty()
    }

    @Test
    fun it_should_returns_host_info() {
        assertThat(client.clientLiveDescription).isEqualTo("2022.3.1 (Community Edition) - " + project.name)
    }

    @Test
    fun it_should_show_message_as_notification() {
        client.showMessage(MessageType.WARNING, "Some message")

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "",
                "Some message"
            )
        )
    }
}
