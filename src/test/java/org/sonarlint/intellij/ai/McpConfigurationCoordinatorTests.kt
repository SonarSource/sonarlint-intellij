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

import com.intellij.notification.NotificationType
import com.fasterxml.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.ai.ide.McpConfigurationService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdateParams
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationUpdatePlanResponse
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

class McpConfigurationCoordinatorTests : AbstractSonarLintLightTests() {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var backend: BackendService
    private lateinit var credentials: CredentialsService
    private lateinit var registry: AiAgentRegistry
    private lateinit var ui: RecordingMcpUi
    private val connection = ServerConnection.newBuilder()
        .setName("connection")
        .setHostUrl("https://sonar.example")
        .build()

    @BeforeEach
    fun prepare() {
        backend = mock()
        credentials = mock()
        registry = mock()
        ui = RecordingMcpUi()
        globalSettings.serverConnections = listOf(connection)
        whenever(registry.detectedIdeAgents()).thenReturn(emptyList())
        whenever(credentials.getCredentials(connection)).thenReturn(Either.forLeft(TokenDto("test-token")))
        whenever(backend.generateMcpConfiguration(eq("connection"), any())).thenReturn(CompletableFuture.completedFuture("generated"))
        whenever(backend.getAiIntegrationState(isNull(), any())).thenReturn(CompletableFuture.completedFuture(baseSnapshot()))
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpConfigurationInspectionResponse(McpConfigurationState.NOT_CONFIGURED, emptyList()))
        )
    }

    @Test
    fun `inspects only standalone-capable agents with known paths and preserves other capabilities`() {
        whenever(registry.standaloneMcpPath(AiAgent.CURSOR)).thenReturn(tempDir.resolve("cursor.json"))
        whenever(registry.standaloneMcpPath(AiAgent.CLAUDE_CODE)).thenReturn(tempDir.resolve("claude.json"))
        whenever(backend.inspectMcpConfiguration(any(), any())).thenAnswer { invocation ->
            val agent = invocation.getArgument<AiAgent>(0)
            CompletableFuture.completedFuture(
                McpConfigurationInspectionResponse(if (agent == AiAgent.CURSOR) McpConfigurationState.MALFORMED else McpConfigurationState.CLI_MANAGED, listOf("diagnostic"))
            )
        }
        val snapshot = baseSnapshot(listOf(
            capability(AiAgent.CURSOR), capability(AiAgent.CLAUDE_CODE), capability(AiAgent.GITHUB_COPILOT), capability(AiAgent.KIRO, false)
        ))

        val inspected = coordinator().inspectSnapshot(snapshot).get()

        assertThat(inspected.mcpConfigurations.keys).containsExactlyInAnyOrder(AiAgent.CURSOR, AiAgent.CLAUDE_CODE)
        assertThat(inspected.agents).isEqualTo(snapshot.agents)
        assertThat(inspected.mcpConfigurations.getValue(AiAgent.CURSOR).state).isEqualTo(McpConfigurationState.MALFORMED)
        assertThat(inspected.mcpConfigurations.getValue(AiAgent.CLAUDE_CODE).state).isEqualTo(McpConfigurationState.CLI_MANAGED)
        verify(backend, never()).inspectMcpConfiguration(eq(AiAgent.GITHUB_COPILOT), any())
        verify(registry, never()).standaloneMcpPath(AiAgent.KIRO)
    }

    @Test
    fun `one failed inspection leaves the other agents in the snapshot`() {
        whenever(registry.standaloneMcpPath(AiAgent.CURSOR)).thenReturn(tempDir.resolve("cursor.json"))
        whenever(registry.standaloneMcpPath(AiAgent.CLAUDE_CODE)).thenReturn(tempDir.resolve("claude.json"))
        whenever(backend.inspectMcpConfiguration(eq(AiAgent.CURSOR), any())).thenReturn(
            CompletableFuture.failedFuture(IllegalStateException("access denied"))
        )

        val inspected = coordinator().inspectSnapshot(baseSnapshot(listOf(capability(AiAgent.CURSOR), capability(AiAgent.CLAUDE_CODE)))).get()

        assertThat(inspected.mcpConfigurations.getValue(AiAgent.CURSOR).state).isEqualTo(McpConfigurationState.UNKNOWN)
        assertThat(inspected.mcpConfigurations.getValue(AiAgent.CURSOR).diagnostics).anyMatch { it.contains("access denied") }
        assertThat(inspected.mcpConfigurations.getValue(AiAgent.CLAUDE_CODE).state).isEqualTo(McpConfigurationState.NOT_CONFIGURED)
    }

    @Test
    fun `creation preserves unrelated content without leaving a backup or temporary file`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "{\"unrelated\":true}")
        plan(McpConfigurationState.NOT_CONFIGURED, "{\"unrelated\":true,\"sonar\":true}")

        val result = coordinator().createConfiguration(project, AiAgent.CURSOR, path, "connection")

        assertThat(result).isEqualTo(McpTransactionResult.Updated)
        assertThat(Files.readString(path)).contains("\"unrelated\":true").contains("\"sonar\":true")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
        verify(backend).generateMcpConfiguration(eq("connection"), any())
        assertThat(ui.tokenWarningRequests).isZero()
    }

    @Test
    fun `creation rereads after token confirmation and never replaces a new standalone entry`() {
        val path = tempDir.resolve("mcp.json")
        whenever(backend.generateMcpConfiguration(any(), any())).thenAnswer {
            Files.writeString(path, "configured by another agent")
            CompletableFuture.completedFuture("generated")
        }
        plan(McpConfigurationState.STANDALONE, "replacement")

        val result = coordinator().createConfiguration(project, AiAgent.CURSOR, path, "connection")

        assertThat(result).isEqualTo(McpTransactionResult.Protected)
        assertThat(Files.readString(path)).isEqualTo("configured by another agent")
        verify(backend).planMcpConfigurationUpdate(AiAgent.CURSOR, "configured by another agent", "generated")
    }

    @Test
    fun `empty token requires explicit consent and sends an empty token when accepted`() {
        val path = tempDir.resolve("mcp.json")
        whenever(credentials.getCredentials(connection)).thenReturn(Either.forLeft(TokenDto("")))
        val coordinator = coordinator()

        assertThat(coordinator.createConfiguration(project, AiAgent.CURSOR, path, "connection")).isEqualTo(McpTransactionResult.Cancelled)
        verify(backend, never()).generateMcpConfiguration(any(), any())
        assertThat(Files.exists(path)).isFalse()

        ui.proceedWithoutToken = true
        plan(McpConfigurationState.NOT_CONFIGURED, "created")
        assertThat(coordinator.createConfiguration(project, AiAgent.CURSOR, path, "connection")).isEqualTo(McpTransactionResult.Updated)
        verify(backend).generateMcpConfiguration("connection", "")
        assertThat(ui.tokenWarningRequests).isEqualTo(2)
    }

    @Test
    fun `username password credentials use the same missing token warning`() {
        whenever(credentials.getCredentials(connection)).thenReturn(Either.forRight(UsernamePasswordDto("user", "test-password")))

        assertThat(coordinator().createConfiguration(project, AiAgent.CURSOR, tempDir.resolve("mcp.json"), "connection"))
            .isEqualTo(McpTransactionResult.Cancelled)
        assertThat(ui.tokenWarningRequests).isEqualTo(1)
        verify(backend, never()).generateMcpConfiguration(any(), any())
    }

    @Test
    fun `existing configuration setup updates only the port without selecting a connection or looking up credentials`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "external")
        plan(McpConfigurationState.STANDALONE, "port refreshed")
        globalSettings.serverConnections = emptyList()
        val coordinator = coordinator()
        coordinator.embeddedServerStarted(64121)
        val snapshot = baseSnapshot().copy(connectionChoices = emptyList(), mcpConfigurations = mapOf(
            AiAgent.CURSOR to McpAgentConfiguration(AiAgent.CURSOR, path, McpConfigurationState.STANDALONE, emptyList())
        ))

        assertThat(coordinator.setUp(project, snapshot, AiAgent.CURSOR)).isTrue()

        assertThat(Files.readString(path)).isEqualTo("port refreshed")
        verify(backend).planMcpConfigurationUpdate(AiAgent.CURSOR, "external", portConfiguration(64121))
        verify(credentials, never()).getCredentials(any())
        verify(backend, never()).generateMcpConfiguration(any(), any())
        assertThat(ui.connectionChoices).isZero()
        assertThat(ui.tokenWarningRequests).isZero()
    }

    @Test
    fun `startup refresh discovers IDE and local agents and updates every supported existing config`() {
        val cursor = tempDir.resolve("cursor.json")
        val claude = tempDir.resolve("claude.json")
        val absent = tempDir.resolve("copilot.json")
        val unsupported = tempDir.resolve("kiro.json")
        Files.writeString(cursor, "external cursor")
        Files.writeString(claude, "external claude")
        Files.writeString(unsupported, "unsupported")
        whenever(registry.detectedIdeAgents()).thenReturn(listOf(AiAgent.GITHUB_COPILOT))
        whenever(registry.standaloneMcpPath(AiAgent.CURSOR)).thenReturn(cursor)
        whenever(registry.standaloneMcpPath(AiAgent.CLAUDE_CODE)).thenReturn(claude)
        whenever(registry.standaloneMcpPath(AiAgent.GITHUB_COPILOT)).thenReturn(absent)
        whenever(registry.standaloneMcpPath(AiAgent.KIRO)).thenReturn(unsupported)
        detected(capability(AiAgent.CURSOR), capability(AiAgent.CLAUDE_CODE), capability(AiAgent.GITHUB_COPILOT), capability(AiAgent.KIRO, false))
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenAnswer { invocation ->
            CompletableFuture.completedFuture(McpConfigurationUpdatePlanResponse(McpConfigurationState.STANDALONE, invocation.getArgument<String>(1) + " refreshed", emptyList()))
        }

        coordinator().embeddedServerStarted(64121)

        assertThat(Files.readString(cursor)).isEqualTo("external cursor refreshed")
        assertThat(Files.readString(claude)).isEqualTo("external claude refreshed")
        assertThat(Files.readString(unsupported)).isEqualTo("unsupported")
        assertThat(Files.exists(absent)).isFalse()
        verify(backend).getAiIntegrationState(null, listOf(AiAgent.GITHUB_COPILOT))
        verify(backend, never()).planMcpConfigurationUpdate(eq(AiAgent.GITHUB_COPILOT), any(), any())
        verify(registry, never()).standaloneMcpPath(AiAgent.KIRO)
        verify(backend, never()).generateMcpConfiguration(any(), any())
        verify(credentials, never()).getCredentials(any())
    }

    @Test
    fun `startup leaves a config without the IDE port field unchanged`() {
        val path = detectedFile()
        val content = """{"mcpServers":{"sonarqube":{"command":"docker","args":["sonarsource/sonarqube-mcp"],"env":{"SONARQUBE_TOKEN":"user-token"}}}}"""
        Files.writeString(path, content)
        useCorePlanner()

        coordinator().embeddedServerStarted(64121)

        assertThat(Files.readString(path)).isEqualTo(content)
        verify(backend).planMcpConfigurationUpdate(AiAgent.CURSOR, content, portConfiguration(64121))
        verify(backend, never()).generateMcpConfiguration(any(), any())
    }

    @Test
    fun `core planner refresh preserves custom launch options credentials and unrelated entries`() {
        val path = detectedFile()
        val content = """
            {
              "custom": "keep",
              "mcpServers": {
                "other": {"command": "custom"},
                "sonarqube": {
                  "command": "custom-docker",
                  "args": ["run", "--network=host", "sonarsource/sonarqube-mcp:custom"],
                  "env": {"SONARQUBE_IDE_PORT": "64120", "SONARQUBE_URL": "https://custom.example", "SONARQUBE_TOKEN": "user-token", "CUSTOM": "keep"}
                }
              }
            }
        """.trimIndent()
        Files.writeString(path, content)
        useCorePlanner()

        coordinator().embeddedServerStarted(64121)

        val mapper = JsonMapper()
        assertThat(mapper.readTree(Files.readString(path))).isEqualTo(mapper.readTree(content.replace("64120", "64121")))
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
        verify(credentials, never()).getCredentials(any())
        verify(backend, never()).generateMcpConfiguration(any(), any())
    }

    @Test
    fun `startup leaves all protected or unconfigured states unchanged`() {
        val path = detectedFile()
        val coordinator = coordinator()
        McpConfigurationState.values().filter { it != McpConfigurationState.STANDALONE }.forEach { state ->
            plan(state, "unsafe replacement")
            coordinator.embeddedServerStarted(64121)
            assertThat(Files.readString(path)).isEqualTo("existing")
        }
    }

    @Test
    fun `startup skips undetected agents and invalid announced ports`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "undetected")
        whenever(registry.standaloneMcpPath(AiAgent.CURSOR)).thenReturn(path)
        val coordinator = coordinator()
        coordinator.embeddedServerStarted(0)
        coordinator.embeddedServerStarted(65536)
        verify(backend, never()).getAiIntegrationState(isNull(), any())

        coordinator.embeddedServerStarted(64121)
        assertThat(Files.readString(path)).isEqualTo("undetected")
        verify(backend, never()).planMcpConfigurationUpdate(any(), any(), any())
    }

    @Test
    fun `one failing agent does not prevent refreshing the others`() {
        val path = detectedFile()
        detected(capability(AiAgent.CLAUDE_CODE), capability(AiAgent.CURSOR))
        whenever(registry.standaloneMcpPath(AiAgent.CLAUDE_CODE)).thenThrow(IllegalStateException("unreadable"))
        plan(McpConfigurationState.STANDALONE, "refreshed")

        coordinator().embeddedServerStarted(64121)

        assertThat(Files.readString(path)).isEqualTo("refreshed")
    }

    @Test
    fun `newest announced port supersedes a refresh still being planned`() {
        val path = detectedFile()
        val writtenPorts = mutableListOf<Int>()
        val fileSystem = object : McpFileSystem by NioMcpFileSystem() {
            override fun replace(temp: Path, target: Path) {
                writtenPorts += if (Files.readString(temp) == portConfiguration(64122)) 64122 else 64121
                NioMcpFileSystem().replace(temp, target)
            }
        }
        val updatingCoordinator = coordinator(fileSystem = fileSystem)
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenAnswer { invocation ->
            val desired = invocation.getArgument<String>(2)
            if (desired == portConfiguration(64121)) updatingCoordinator.embeddedServerStarted(64122)
            CompletableFuture.completedFuture(McpConfigurationUpdatePlanResponse(McpConfigurationState.STANDALONE, desired, emptyList()))
        }

        updatingCoordinator.embeddedServerStarted(64121)

        assertThat(Files.readString(path)).isEqualTo(portConfiguration(64122))
        assertThat(writtenPorts).containsExactly(64122)
    }

    @Test
    fun `compare and swap aborts when the file changes after planning`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenAnswer {
            Files.writeString(path, "concurrent edit")
            CompletableFuture.completedFuture(McpConfigurationUpdatePlanResponse(McpConfigurationState.NOT_CONFIGURED, "planned", emptyList()))
        }

        val result = coordinator().safeUpdate(AiAgent.CURSOR, path, "generated")

        assertThat(result).isEqualTo(McpTransactionResult.ConcurrentEdit)
        assertThat(Files.readString(path)).isEqualTo("concurrent edit")
    }

    @Test
    fun `compare and swap rechecks after temp creation and removes the temporary file`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        plan(McpConfigurationState.NOT_CONFIGURED, "planned")
        val fileSystem = MutatingAfterTempFileSystem(path)

        val result = coordinator(fileSystem = fileSystem).safeUpdate(AiAgent.CURSOR, path, "generated")

        assertThat(result).isEqualTo(McpTransactionResult.ConcurrentEdit)
        assertThat(Files.readString(path)).isEqualTo("changed after temp creation")
        assertThat(Files.exists(fileSystem.temp!!)).isFalse()
    }

    @Test
    fun `symbolic link is refused before content is read or planned`() {
        val target = tempDir.resolve("target.json")
        val link = tempDir.resolve("mcp.json")
        Files.writeString(target, "external")
        Files.createSymbolicLink(link, target)

        assertThat(coordinator().safeUpdate(AiAgent.CURSOR, link, "generated")).isEqualTo(McpTransactionResult.SymlinkRefused)
        assertThat(Files.readString(target)).isEqualTo("external")
        verify(backend, never()).planMcpConfigurationUpdate(any(), any(), any())
    }

    @Test
    fun `unchanged port refresh does not write a temporary file or replace the configuration`() {
        val path = detectedFile()
        plan(McpConfigurationState.STANDALONE, "existing")
        val fileSystem = mock<McpFileSystem>()
        whenever(fileSystem.read(path)).thenReturn("existing".toByteArray())

        coordinator(fileSystem = fileSystem).embeddedServerStarted(64121)

        assertThat(Files.readString(path)).isEqualTo("existing")
        verify(fileSystem, never()).writeSiblingTemp(any(), any())
        verify(fileSystem, never()).replace(any(), any())
    }

    @Test
    fun `failed final move cleans the sibling temp and preserves the original`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        plan(McpConfigurationState.NOT_CONFIGURED, "after")
        val fileSystem = FailingReplaceFileSystem()

        assertThatThrownBy { coordinator(fileSystem = fileSystem).safeUpdate(AiAgent.CURSOR, path, "generated") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(Files.exists(fileSystem.temp!!)).isFalse()
        assertThat(Files.readString(path)).isEqualTo("before")
    }

    @Test
    fun `same normalized path is serialized`() {
        val pool = Executors.newFixedThreadPool(2)
        val coordinator = coordinator(executor = pool)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        try {
            val first = coordinator.executeSerialized(tempDir.resolve("a/../mcp.json")) {
                maxActive.updateAndGet { maxOf(it, active.incrementAndGet()) }
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                active.decrementAndGet()
                McpTransactionResult.Unchanged
            }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            val second = coordinator.executeSerialized(tempDir.resolve("mcp.json")) {
                maxActive.updateAndGet { maxOf(it, active.incrementAndGet()) }
                active.decrementAndGet()
                McpTransactionResult.Unchanged
            }
            release.countDown()
            CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS)
            assertThat(maxActive.get()).isEqualTo(1)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `missing MCP connection opens settings and project binding takes priority for creation`() {
        val path = tempDir.resolve("mcp.json")
        val snapshot = baseSnapshot().copy(mcpConfigurations = mapOf(
            AiAgent.CURSOR to McpAgentConfiguration(AiAgent.CURSOR, path, McpConfigurationState.NOT_CONFIGURED, emptyList())
        ))
        val coordinator = coordinator()
        globalSettings.serverConnections = emptyList()
        assertThat(coordinator.setUp(project, snapshot.copy(connectionChoices = emptyList()), AiAgent.CURSOR)).isFalse()
        assertThat(ui.settingsOpened).isTrue()

        globalSettings.serverConnections = listOf(
            connection,
            ServerConnection.newBuilder().setName("recommended").setHostUrl("https://recommended.example").build()
        )
        getSettingsFor(project).connectionName = "connection"
        plan(McpConfigurationState.NOT_CONFIGURED, "created")
        val choices = snapshot.copy(
            connectionChoices = snapshot.connectionChoices + IntegrationConnection("recommended", "https://sonar.example", null),
            recommendedConnectionId = "recommended"
        )
        assertThat(coordinator.setUp(project, choices, AiAgent.CURSOR)).isTrue()
        verify(backend).generateMcpConfiguration(eq("connection"), any())
        assertThat(ui.connectionChoices).isZero()
        assertThat(Files.readString(path)).isEqualTo("created")
    }

    @ParameterizedTest
    @EnumSource(CliAuthenticationStatus::class)
    fun `MCP setup offers saved server and cloud connections independently of CLI authentication`(authentication: CliAuthenticationStatus) {
        val cloud = ServerConnection.newBuilder().setName("cloud").setHostUrl("https://sonarcloud.io").setOrganizationKey("organization").build()
        globalSettings.serverConnections = listOf(connection, cloud)
        ui.selectedConnectionId = connection.name
        val path = tempDir.resolve("mcp.json")
        plan(McpConfigurationState.NOT_CONFIGURED, "created")
        val snapshot = baseSnapshot().let { base ->
            base.copy(
                cli = base.cli.copy(authentication = authentication),
                connectionChoices = emptyList(),
                mcpConfigurations = mapOf(
                    AiAgent.CURSOR to McpAgentConfiguration(AiAgent.CURSOR, path, McpConfigurationState.NOT_CONFIGURED, emptyList())
                )
            )
        }

        assertThat(coordinator().setUp(project, snapshot, AiAgent.CURSOR)).isTrue()

        assertThat(ui.offeredConnections.map { it.connectionId }).containsExactly("connection", "cloud")
        assertThat(ui.offeredConnections.last().organization).isEqualTo("organization")
        verify(backend).generateMcpConfiguration("connection", "test-token")
        assertThat(Files.readString(path)).isEqualTo("created")
    }

    @Test
    fun `every setup result and unexpected failure has actionable feedback`() {
        val coordinator = coordinator()
        McpTransactionResult.entries.forEach { coordinator.reportSetupResult(project, it, null) }
        coordinator.reportSetupResult(project, null, IllegalStateException("planning failed"))

        assertThat(ui.messages).hasSize(McpTransactionResult.entries.size + 1)
        assertThat(ui.messages.map { it.message }).allSatisfy { assertThat(it).isNotBlank() }
        assertThat(ui.messages.map { it.type }).contains(NotificationType.ERROR, NotificationType.WARNING)
        assertThat(ui.settingsOpened).isTrue()
    }

    private fun coordinator(
        fileSystem: McpFileSystem = NioMcpFileSystem(),
        executor: java.util.concurrent.Executor = java.util.concurrent.Executor { it.run() }
    ) = McpConfigurationCoordinator(backend, registry, credentials, fileSystem, ui, executor)

    private fun plan(state: McpConfigurationState, content: String?) {
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpConfigurationUpdatePlanResponse(state, content, emptyList()))
        )
    }

    private fun useCorePlanner() {
        val planner = McpConfigurationService()
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenAnswer { invocation ->
            val response = planner.planUpdate(McpConfigurationUpdateParams(
                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)
            ))
            CompletableFuture.completedFuture(response)
        }
    }

    private fun detected(vararg agents: AgentCapability) {
        whenever(backend.getAiIntegrationState(isNull(), any())).thenReturn(CompletableFuture.completedFuture(baseSnapshot(agents.toList())))
    }

    private fun detectedFile(): Path = tempDir.resolve("mcp.json").also {
        Files.writeString(it, "existing")
        whenever(registry.standaloneMcpPath(AiAgent.CURSOR)).thenReturn(it)
        detected(capability(AiAgent.CURSOR))
    }

    private fun baseSnapshot(agents: List<AgentCapability> = emptyList()) = AiIntegrationSnapshot(
        CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, null, null, null),
        agents,
        listOf(IntegrationConnection("connection", "https://sonar.example", null)),
        null
    )

    private fun capability(agent: AiAgent, standalone: Boolean = true) =
        AgentCapability(agent, emptySet(), cliIntegrationSupported = false, standaloneMcpSupported = standalone)

    private fun portConfiguration(port: Int) = """{"env":{"SONARQUBE_IDE_PORT":"$port"}}"""
}

private class RecordingMcpUi : McpUiAdapter {
    var settingsOpened = false
    var proceedWithoutToken = false
    var tokenWarningRequests = 0
    var connectionChoices = 0
    var offeredConnections: List<IntegrationConnection> = emptyList()
    var selectedConnectionId: String? = null
    val messages = mutableListOf<UiMessage>()

    override fun chooseConnection(project: com.intellij.openapi.project.Project, connections: List<IntegrationConnection>): String? {
        connectionChoices++
        offeredConnections = connections
        return selectedConnectionId
    }

    override fun confirmWithoutToken(project: com.intellij.openapi.project.Project): Boolean {
        tokenWarningRequests++
        return proceedWithoutToken
    }

    override fun openConfiguration(project: com.intellij.openapi.project.Project, path: Path) = Unit

    override fun openConnectionSettings(project: com.intellij.openapi.project.Project) {
        settingsOpened = true
    }

    override fun showMessage(project: com.intellij.openapi.project.Project, message: String, type: NotificationType) {
        messages += UiMessage(message, type)
    }

    data class UiMessage(val message: String, val type: NotificationType)
}

private class FailingReplaceFileSystem(private val delegate: McpFileSystem = NioMcpFileSystem()) : McpFileSystem by delegate {
    var temp: Path? = null

    override fun writeSiblingTemp(path: Path, content: ByteArray): Path =
        delegate.writeSiblingTemp(path, content).also { temp = it }

    override fun replace(temp: Path, target: Path) {
        throw IllegalStateException("move failed")
    }
}

private class MutatingAfterTempFileSystem(private val target: Path, private val delegate: McpFileSystem = NioMcpFileSystem()) : McpFileSystem by delegate {
    var temp: Path? = null

    override fun writeSiblingTemp(path: Path, content: ByteArray): Path =
        delegate.writeSiblingTemp(path, content).also {
            temp = it
            Files.writeString(target, "changed after temp creation")
        }
}
