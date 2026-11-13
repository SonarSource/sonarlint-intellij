/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ai

import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.openapi.util.Disposer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fixtures.awaitUntil
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto

class JetBrainsMcpConfigurationLightTests : AbstractSonarLintLightTests() {
    @TempDir
    lateinit var home: Path
    private val agents = listOf(AiAgent.JUNIE, AiAgent.JETBRAINS_AI_ASSISTANT)
    private val backend get() = getService(BackendService::class.java)
    private val registry = mock<AiAgentRegistry>()
    private val credentials = mock<CredentialsService>()
    private val ui = mock<McpUiAdapter>()
    private val telemetry = mock<SonarLintTelemetry>()
    private lateinit var coordinator: McpConfigurationCoordinator
    private lateinit var snapshot: AiIntegrationSnapshot
    private val observedStates = ConcurrentHashMap<AiAgent, McpConfigurationState>()

    @BeforeEach
    fun prepare() {
        val paths = AiAgentRegistry { false }
        agents.forEach { agent ->
            whenever(registry.standaloneMcpPath(agent)).thenReturn(
                paths.standaloneMcpPath(agent, home, false, null, assistantPath = ".ai/mcp/mcp.json")
            )
            whenever(registry.displayName(agent)).thenReturn(paths.displayName(agent))
        }
        whenever(registry.detectedIdeAgents()).thenReturn(agents)
        val connection = ServerConnection.newBuilder().setName("connection").setHostUrl("http://localhost:1").build()
        globalSettings.serverConnections = listOf(connection)
        backend.connectionsUpdated(listOf(connection))
        whenever(credentials.getCredentials(connection)).thenReturn(Either.forLeft(TokenDto("test-token")))
        replaceApplicationService(SonarLintTelemetry::class.java, telemetry)
        whenever(telemetry.aiAgentIntegrationStateObserved(any(), any(), any())).thenAnswer { invocation ->
            observedStates[invocation.getArgument(0)] = invocation.getArgument(2)
            null
        }
        coordinator = McpConfigurationCoordinator(backend, registry, credentials, ui, Runnable::run)
        snapshot = backend.getAiIntegrationState(project, agents).join()
        assertThat(snapshot.agents.filter { it.agent in agents }).hasSize(2).allMatch { it.standaloneMcpSupported }
    }

    @ParameterizedTest
    @EnumSource(value = AiAgent::class, names = ["JUNIE", "JETBRAINS_AI_ASSISTANT"])
    fun `creates missing configuration files and parent directories`(agent: AiAgent) {
        val path = registry.standaloneMcpPath(agent)!!
        assertThat(path).doesNotExist()
        val missing = coordinator.inspectSnapshot(snapshot).join()

        assertThat(coordinator.setUp(project, missing, agent)).isTrue()

        assertThat(path).isRegularFile()
        assertThat(coordinator.inspectSnapshot(snapshot).join().mcpConfigurations.getValue(agent).state)
            .isEqualTo(McpConfigurationState.STANDALONE)
    }

    @ParameterizedTest
    @EnumSource(value = AiAgent::class, names = ["JUNIE", "JETBRAINS_AI_ASSISTANT"])
    fun `sets up MCP while preserving other servers`(agent: AiAgent) {
        val path = registry.standaloneMcpPath(agent)!!
        val missing = coordinator.inspectSnapshot(snapshot).join()
        assertThat(missing.mcpConfigurations.getValue(agent).state).isEqualTo(McpConfigurationState.NOT_CONFIGURED)
        assertObservedState(agent, McpConfigurationState.NOT_CONFIGURED)
        Files.createDirectories(path.parent)
        Files.writeString(path, """{"custom":"keep","mcpServers":{"other":{"command":"custom"}}}""")

        assertThat(coordinator.setUp(project, missing, agent)).isTrue()

        val configured = coordinator.inspectSnapshot(snapshot).join()
        assertThat(configured.mcpConfigurations.getValue(agent).state).isEqualTo(McpConfigurationState.STANDALONE)
        val content = JsonMapper().readTree(Files.readString(path))
        assertThat(content.path("custom").asText()).isEqualTo("keep")
        assertThat(content.path("mcpServers").path("other").path("command").asText()).isEqualTo("custom")
        assertThat(content.path("mcpServers").path("sonarqube").path("env").path("SONARQUBE_TOKEN").asText()).isEqualTo("test-token")
        whenever(ui.openConfiguration(project, path)).thenReturn(true)
        coordinator.openConfiguration(project, agent, configured)
        verify(ui).openConfiguration(project, path)
        assertObservedState(agent, McpConfigurationState.STANDALONE)
    }

    @ParameterizedTest
    @EnumSource(value = AiAgent::class, names = ["JUNIE", "JETBRAINS_AI_ASSISTANT"])
    fun `keeps malformed CLI-managed and unrecognized configurations protected`(agent: AiAgent) {
        val path = registry.standaloneMcpPath(agent)!!
        Files.createDirectories(path.parent)
        mapOf(
            McpConfigurationState.MALFORMED to "{bad json",
            McpConfigurationState.CLI_MANAGED to """{"mcpServers":{"sonarqube":{"command":"sonar","args":["run","mcp"]}}}""",
            McpConfigurationState.UNKNOWN to """{"mcpServers":{"sonarqube":{"command":"custom"}}}"""
        ).forEach { (state, content) ->
            Files.writeString(path, content)
            val inspected = coordinator.inspectSnapshot(snapshot).join()
            assertThat(inspected.mcpConfigurations.getValue(agent).state).isEqualTo(state)

            assertThat(coordinator.setUp(project, inspected, agent)).isFalse()
            coordinator.embeddedServerStarted(64121)

            assertThat(Files.readString(path)).isEqualTo(content)
            assertObservedState(agent, state)
        }
    }

    @ParameterizedTest
    @EnumSource(value = AiAgent::class, names = ["JUNIE", "JETBRAINS_AI_ASSISTANT"])
    fun `refreshes the IDE port without changing custom configuration`(agent: AiAgent) {
        val path = registry.standaloneMcpPath(agent)!!
        Files.createDirectories(path.parent)
        val content = """
            {"custom":"keep","mcpServers":{"other":{"command":"custom"},"sonarqube":{
              "command":"docker","args":["run","sonarsource/sonarqube-mcp:custom"],
              "env":{"SONARQUBE_IDE_PORT":"64120","SONARQUBE_TOKEN":"keep-token","CUSTOM":"keep"}
            }}}
        """.trimIndent()
        Files.writeString(path, content)

        coordinator.embeddedServerStarted(64121)

        val mapper = JsonMapper()
        assertThat(mapper.readTree(Files.readString(path))).isEqualTo(mapper.readTree(content.replace("64120", "64121")))
        assertObservedState(agent, McpConfigurationState.STANDALONE)
    }

    private fun assertObservedState(agent: AiAgent, state: McpConfigurationState) {
        observedStates.clear()
        val controller = AiIntegrationsController(
            project, AiIntegrationsPanel(registry), backendService = backend, registry = registry,
            telemetry = telemetry, mcpCoordinator = McpConfigurationCoordinator(backend, registry, credentials, ui)
        )
        try {
            controller.loadInitially()
            awaitUntil { observedStates[agent] == state }
            assertThat(observedStates[agent]).isEqualTo(state)
        } finally {
            Disposer.dispose(controller)
        }
    }
}
