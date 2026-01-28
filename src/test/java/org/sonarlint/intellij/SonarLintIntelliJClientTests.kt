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
package org.sonarlint.intellij

import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import java.nio.file.Paths
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.sonarlint.intellij.actions.OpenTrackedLinkAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.sca.DependencyRisksCache
import org.sonarlint.intellij.finding.sca.aDependencyRiskDto
import org.sonarlint.intellij.notifications.GenerateTokenAction
import org.sonarlint.intellij.notifications.OpenProjectSettingsAction
import org.sonarlint.intellij.promotion.UtmParameters
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageActionItem
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

class SonarLintIntelliJClientTests : AbstractSonarLintLightTests() {
    lateinit var client: SonarLintIntelliJClient

    @BeforeEach
    fun prepare() {
        // Important as this starts the notification manager service
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
                projectBackendId to listOf(BindingSuggestionDto("connectionId", "projectKey", "projectName", BindingSuggestionOrigin.PROPERTIES_FILE))
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
                    BindingSuggestionDto("connectionId", "projectKey", "projectName", BindingSuggestionOrigin.PROPERTIES_FILE),
                    BindingSuggestionDto("connectionId", "projectKey2", "projectName2", BindingSuggestionOrigin.PROPERTIES_FILE)
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
                    BindingSuggestionDto("connectionId", "projectKey", "projectName", BindingSuggestionOrigin.PROPERTIES_FILE),
                    BindingSuggestionDto("connectionId", "projectKey2", "projectName2", BindingSuggestionOrigin.PROPERTIES_FILE)
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
                    BindingSuggestionDto("connectionId", "projectKey", "projectName", BindingSuggestionOrigin.PROPERTIES_FILE),
                    BindingSuggestionDto("connectionId", "projectKey2", "projectName2", BindingSuggestionOrigin.PROPERTIES_FILE)
                )
            )
        )

        assertThat(projectNotifications).isEmpty()
    }

    @Test
    fun it_should_returns_host_info() {
        assertThat(client.clientLiveDescription).isEqualTo("2023.1.7 (Community Edition) - " + project.name)
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

    @Test
    fun should_notify_unsatisfied_jre() {
        client.didSkipLoadingPlugin(projectBackendId, Language.JAVA, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, "1.0", "0.2")

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "<b>SonarQube for IDE failed to analyze Java code</b>",
                "SonarQube for IDE requires Java runtime version 1.0 or later to analyze Java code. Current version is 0.2."
            )
        )
    }

    @Test
    fun should_notify_unsatisfied_node_js() {
        client.didSkipLoadingPlugin(projectBackendId, Language.JS, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS, "1.0", "0.2")

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "<b>SonarQube for IDE failed to analyze JavaScript code</b>",
                "SonarQube for IDE requires Node.js runtime version 1.0 or later to analyze JavaScript code. " +
                    "Current version is 0.2.<br>Please configure the Node.js path in the SonarQube for IDE settings."
            )
        )
    }

    @Test
    fun should_notify_detected_secret() {
        client.didDetectSecret(projectBackendId)

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "SonarQube for IDE: secret(s) detected",
                "SonarQube for IDE detected some secrets in one of the open files. " +
                    "We strongly advise you to review those secrets and ensure they are not committed into repositories. " +
                    "Please refer to the SonarQube for IDE tool window for more information."
            )
        )
    }

    @Test
    fun should_promote_extra_languages() {
        client.promoteExtraEnabledLanguagesInConnectedMode(projectBackendId, setOf(Language.ANSIBLE, Language.SCALA))

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "<b>SonarQube for IDE suggestions</b>",
                "Enable Ansible / Scala analysis by connecting your project"
            )
        )
    }

    @Test
    fun should_promote_languages_with_utm_params() {
        client.promoteExtraEnabledLanguagesInConnectedMode(projectBackendId, setOf(Language.ANSIBLE, Language.SCALA))
        val action = projectNotifications[0].actions[0]

        assertThat(action).isInstanceOf(OpenTrackedLinkAction::class.java)
        assertThat((action as OpenTrackedLinkAction).utmParameters).isEqualTo(UtmParameters.ENABLE_LANGUAGE_ANALYSIS)
    }

    @Test
    fun should_find_project_basedir() {
        val expectedBaseDir = Paths.get(project.guessProjectDir()!!.path)
        val baseDir = client.getBaseDir(projectBackendId)

        assertThat(baseDir).isEqualTo(expectedBaseDir)
    }

    @Test
    fun should_find_module_basedir() {
        val expectedBaseDir = Paths.get(module.guessModuleDir()!!.path)
        val baseDir = client.getBaseDir(BackendService.moduleId(module))

        assertThat(baseDir).isEqualTo(expectedBaseDir)
    }

    @Test
    fun should_not_find_unknown_config_scope_basedir() {
        Assertions.assertThrows(ConfigScopeNotFoundException::class.java) { client.getBaseDir("unknownId") }
    }

    @Test
    fun it_should_send_invalid_token_notification() {
        globalSettings.serverConnections = listOf(ServerConnection.newBuilder().setName("connectionId").build())
        client.invalidToken("connectionId")

        assertThat(projectNotifications).extracting("title", "content").containsExactly(
            tuple(
                "",
                "The token used for connection 'connectionId' is invalid, please update your credentials"
            )
        )
    }

    @Test
    fun it_should_include_generate_token_action_in_invalid_token_notification() {
        val connection = ServerConnection.newBuilder().setName("connectionId").setHostUrl("https://sonarcloud.io").build()
        globalSettings.serverConnections = listOf(connection)
        
        client.invalidToken("connectionId")

        assertThat(projectNotifications).hasSize(1)
        val notification = projectNotifications[0]
        assertThat(notification.actions).hasSize(2)
        assertThat(notification.actions[0]).isInstanceOf(GenerateTokenAction::class.java)
        assertThat(notification.actions[1]).isInstanceOf(OpenProjectSettingsAction::class.java)
    }

    @Test
    fun should_handle_dependency_risks_changes() {
        val toolWindow = mock(SonarLintToolWindow::class.java)
        replaceProjectService(SonarLintToolWindow::class.java, toolWindow)
        val risksCache = mock(DependencyRisksCache::class.java)
        replaceProjectService(DependencyRisksCache::class.java, risksCache)

        val closedRiskId1 = UUID.randomUUID()
        val closedRiskId2 = UUID.randomUUID()
        val closedRiskIds = setOf(closedRiskId1, closedRiskId2)

        val addedRiskId1 = UUID.randomUUID()
        val addedRiskId2 = UUID.randomUUID()
        val addedRisk1 = aDependencyRiskDto(DependencyRiskDto.Status.OPEN, listOf(), DependencyRiskDto.Severity.HIGH, addedRiskId1)
        val addedRisk2 = aDependencyRiskDto(DependencyRiskDto.Status.OPEN, listOf(), DependencyRiskDto.Severity.HIGH, addedRiskId2)
        val addedRisks = listOf(addedRisk1, addedRisk2)

        val updatedRiskId1 = UUID.randomUUID()
        val updatedRisk1 = aDependencyRiskDto(DependencyRiskDto.Status.SAFE, listOf(), DependencyRiskDto.Severity.HIGH, updatedRiskId1)
        val updatedRisks = listOf(updatedRisk1)

        client.didChangeDependencyRisks(projectBackendId, closedRiskIds, addedRisks, updatedRisks)

        verify(risksCache).update(
            eq(closedRiskIds),
            argThat { addedLocal ->
                addedLocal.size == 2 &&
                addedLocal[0].getId() == addedRiskId1 && !addedLocal[0].isResolved() &&
                addedLocal[1].getId() == addedRiskId2 && !addedLocal[1].isResolved()
            },
            argThat { updatedLocal ->
                updatedLocal.size == 1 &&
                updatedLocal[0].getId() == updatedRiskId1 && updatedLocal[0].isResolved()
            }
        )
        verify(toolWindow).refreshViews()
    }

    @Test
    fun should_not_handle_dependency_risks_changes_for_unknown_project() {
        val closedRiskIds = setOf(UUID.randomUUID())
        val addedRisks = listOf(aDependencyRiskDto(DependencyRiskDto.Status.OPEN, listOf()))
        val updatedRisks = emptyList<DependencyRiskDto>()

        assertDoesNotThrow { client.didChangeDependencyRisks("unknown-project-id", closedRiskIds, addedRisks, updatedRisks) }
    }

    @Test
    fun should_handle_empty_dependency_risks_changes() {
        val toolWindow = mock(SonarLintToolWindow::class.java)
        replaceProjectService(SonarLintToolWindow::class.java, toolWindow)
        val risksCache = mock(DependencyRisksCache::class.java)
        replaceProjectService(DependencyRisksCache::class.java, risksCache)

        client.didChangeDependencyRisks(projectBackendId, emptySet(), emptyList(), emptyList())

        verify(risksCache).update(emptySet(), emptyList(), emptyList())
        verify(toolWindow).refreshViews()
    }

    @Test
    fun should_return_file_exclusions_for_module() {
        setProjectLevelExclusions(listOf("DIRECTORY:wwwroot", "FILE:test.js"))
        globalSettings.fileExclusions = listOf("GLOB:**/*.min.js")

        val exclusions = client.getFileExclusions(BackendService.moduleId(module))

        assertThat(exclusions).contains("**/wwwroot/**")
        assertThat(exclusions).contains("**/test.js")
        assertThat(exclusions).contains("GLOB:**/*.min.js")
    }

    @Test
    fun should_return_file_exclusions_for_project() {
        setProjectLevelExclusions(listOf("DIRECTORY:build", "FILE:generated.java"))
        globalSettings.fileExclusions = listOf("GLOB:**/target/**")

        val exclusions = client.getFileExclusions(projectBackendId)

        assertThat(exclusions).contains("**/build/**")
        assertThat(exclusions).contains("**/generated.java")
        assertThat(exclusions).contains("GLOB:**/target/**")
    }

    @Test
    fun should_return_only_global_exclusions_when_no_project_exclusions() {
        setProjectLevelExclusions(emptyList())
        globalSettings.fileExclusions = listOf("GLOB:**/*.log")

        val exclusions = client.getFileExclusions(BackendService.moduleId(module))

        assertThat(exclusions).containsOnly("GLOB:**/*.log")
    }

    @Test
    fun should_normalize_directory_exclusion_patterns() {
        setProjectLevelExclusions(listOf("DIRECTORY:wwwroot"))

        val exclusions = client.getFileExclusions(BackendService.moduleId(module))

        assertThat(exclusions).contains("**/wwwroot/**")
        assertThat(exclusions).doesNotContain("DIRECTORY:wwwroot")
    }

    @Test
    fun should_normalize_file_exclusion_patterns() {
        setProjectLevelExclusions(listOf("FILE:wwwroot/app.js"))

        val exclusions = client.getFileExclusions(BackendService.moduleId(module))

        assertThat(exclusions).contains("**/wwwroot/app.js")
        assertThat(exclusions).doesNotContain("FILE:wwwroot/app.js")
    }

    @Test
    fun should_handle_windows_paths_in_exclusions() {
        setProjectLevelExclusions(listOf("DIRECTORY:wwwroot\\dist", "FILE:src\\main\\test.js"))

        val exclusions = client.getFileExclusions(BackendService.moduleId(module))

        assertThat(exclusions).anyMatch { it.contains("wwwroot/dist") && !it.contains("\\") }
        assertThat(exclusions).anyMatch { it.contains("src/main/test.js") && !it.contains("\\") }
    }

    @Test
    fun should_show_message_request_with_no_actions() {
        val response = client.showMessageRequest(MessageType.INFO, "Test message", emptyList())

        assertThat(response).isNotNull
        assertThat(response.selectedKey).isNull()
        assertThat(projectNotifications.first().content).isEqualTo("Test message")
    }

    @Test
    fun should_show_message_request_with_actions() {
        val action1 = MessageActionItem("key1", "Action 1", false)
        val action2 = MessageActionItem("key2", "Action 2", true)

        client.showMessageRequest(
            MessageType.INFO,
            "Please choose an option",
            listOf(action1, action2)
        )

        assertThat(projectNotifications).extracting("content").containsExactly("Please choose an option")
        assertThat(projectNotifications).hasSize(1)
        assertThat(projectNotifications.first().actions).hasSize(2)
    }

}
