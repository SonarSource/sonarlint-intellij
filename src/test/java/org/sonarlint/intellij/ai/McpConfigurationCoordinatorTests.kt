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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.UIUtil
import com.fasterxml.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemLoopException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.atLeastOnce
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
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus
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
    private val telemetry = mock<SonarLintTelemetry>()

    @BeforeEach
    fun mockTelemetry() {
        replaceApplicationService(SonarLintTelemetry::class.java, telemetry)
    }

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
    fun `new configuration files are created with owner only POSIX permissions`() {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView(PosixFileAttributeView::class.java))
        val path = tempDir.resolve("settings/mcp.json")
        plan(McpConfigurationState.NOT_CONFIGURED, "created with token")

        assertThat(coordinator().createConfiguration(project, AiAgent.CURSOR, path, "connection"))
            .isEqualTo(McpTransactionResult.Updated)

        assertThat(Files.getPosixFilePermissions(path)).isEqualTo(PosixFilePermissions.fromString("rw-------"))
        assertThat(Files.readString(path)).isEqualTo("created with token")
        Files.list(path.parent).use { assertThat(it).containsExactly(path) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["rw-------", "rw-r-----", "rw-r--r--"])
    fun `atomic replacement preserves existing POSIX permissions and ownership`(permissions: String) {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView(PosixFileAttributeView::class.java))
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
        val original = Files.getFileAttributeView(path, PosixFileAttributeView::class.java).readAttributes()
        plan(McpConfigurationState.NOT_CONFIGURED, "updated")

        assertThat(coordinator().createConfiguration(project, AiAgent.CURSOR, path, "connection"))
            .isEqualTo(McpTransactionResult.Updated)

        val actual = Files.getFileAttributeView(path, PosixFileAttributeView::class.java).readAttributes()
        assertThat(actual.permissions()).isEqualTo(original.permissions())
        assertThat(actual.owner()).isEqualTo(original.owner())
        assertThat(actual.group()).isEqualTo(original.group())
        assertThat(Files.readString(path)).isEqualTo("updated")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
    }

    @Test
    fun `atomic replacement preserves an existing ACL`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
        assumeTrue(view != null)
        val original = view!!.acl.filter { it.principal() == view.owner }
        assumeTrue(original.isNotEmpty())
        view.acl = original
        plan(McpConfigurationState.NOT_CONFIGURED, "updated")

        assertThat(coordinator().createConfiguration(project, AiAgent.CURSOR, path, "connection"))
            .isEqualTo(McpTransactionResult.Updated)

        assertThat(Files.getFileAttributeView(path, AclFileAttributeView::class.java).acl).isEqualTo(original)
        assertThat(Files.readString(path)).isEqualTo("updated")
    }

    @Test
    fun `concurrent readers only see complete configuration files during replacement`() {
        val path = tempDir.resolve("mcp.json")
        val before = "before" + "a".repeat(256 * 1024)
        val updated = "after" + "b".repeat(256 * 1024)
        Files.writeString(path, before)
        val pool = Executors.newSingleThreadExecutor()
        val reading = AtomicBoolean(true)
        val started = CountDownLatch(1)
        val reader = pool.submit {
            do {
                assertThat(Files.readString(path)).isIn(before, updated)
                started.countDown()
                // Give Windows a chance to replace the file between read handles.
                if (SystemInfo.isWindows) Thread.sleep(1)
            } while (reading.get())
        }
        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
            val writer = coordinator()
            repeat(32) {
                plan(McpConfigurationState.NOT_CONFIGURED, if (it % 2 == 0) updated else before)
                assertThat(writer.createConfiguration(project, AiAgent.CURSOR, path, "connection"))
                    .isEqualTo(McpTransactionResult.Updated)
            }
        } finally {
            reading.set(false)
            pool.shutdown()
        }
        reader.get(10, TimeUnit.SECONDS)
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
    }

    @Test
    fun `Windows replacement succeeds after a reader releases its handle`() {
        assumeTrue(SystemInfo.isWindows)
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        plan(McpConfigurationState.NOT_CONFIGURED, "updated")
        val denied = CountDownLatch(1)
        val writer = McpConfigurationCoordinator(backend, registry, credentials, ui, java.util.concurrent.Executor { it.run() }) { source, target ->
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AccessDeniedException) {
                denied.countDown()
                throw error
            }
        }
        val pool = Executors.newSingleThreadExecutor()
        val reader = Files.newByteChannel(path)
        try {
            val update = pool.submit<McpTransactionResult> { writer.createConfiguration(project, AiAgent.CURSOR, path, "connection") }
            assertThat(denied.await(10, TimeUnit.SECONDS)).isTrue()
            assertThat(Files.readString(path)).isEqualTo("before")
            reader.close()
            assertThat(update.get(10, TimeUnit.SECONDS)).isEqualTo(McpTransactionResult.Updated)
        } finally {
            reader.close()
            pool.shutdownNow()
        }
        assertThat(Files.readString(path)).isEqualTo("updated")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
    }

    @Test
    fun `atomic replacement retries a temporary sharing violation`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        plan(McpConfigurationState.NOT_CONFIGURED, "updated")
        var deniedMoves = 0
        val writer = McpConfigurationCoordinator(backend, registry, credentials, ui, java.util.concurrent.Executor { it.run() }) { source, target ->
            if (deniedMoves++ < 2) throw AccessDeniedException(target.toString())
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

        assertThat(writer.createConfiguration(project, AiAgent.CURSOR, path, "connection"))
            .isEqualTo(McpTransactionResult.Updated)

        assertThat(Files.readString(path)).isEqualTo("updated")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
    }

    @Test
    fun `permanent replacement denial preserves the original and removes the temporary file`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        plan(McpConfigurationState.NOT_CONFIGURED, "updated")
        val denied = AccessDeniedException(path.toString())
        val writer = McpConfigurationCoordinator(backend, registry, credentials, ui, java.util.concurrent.Executor { it.run() }) { _, _ -> throw denied }

        assertThatThrownBy { writer.createConfiguration(project, AiAgent.CURSOR, path, "connection") }
            .isSameAs(denied)

        assertThat(Files.readString(path)).isEqualTo("before")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
    }

    @Test
    fun `replacement retries abort when another writer edits the configuration`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        plan(McpConfigurationState.NOT_CONFIGURED, "updated")
        val writer = McpConfigurationCoordinator(backend, registry, credentials, ui, java.util.concurrent.Executor { it.run() }) { _, _ ->
            Files.writeString(path, "concurrent edit")
            throw AccessDeniedException(path.toString())
        }

        assertThat(writer.createConfiguration(project, AiAgent.CURSOR, path, "connection"))
            .isEqualTo(McpTransactionResult.ConcurrentEdit)

        assertThat(Files.readString(path)).isEqualTo("concurrent edit")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
    }

    @Test
    fun `failed temporary writes preserve the original and remove the temporary file`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        plan(McpConfigurationState.NOT_CONFIGURED, "\uD800")

        assertThatThrownBy { coordinator().createConfiguration(project, AiAgent.CURSOR, path, "connection") }
            .isInstanceOf(IOException::class.java)

        assertThat(Files.readString(path)).isEqualTo("before")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
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
    fun `pending token consent and configuration generation do not block inspection`() {
        val pool = Executors.newFixedThreadPool(2)
        val consentRequested = CountDownLatch(1)
        val consentRelease = CountDownLatch(1)
        val generationRequested = CountDownLatch(1)
        val generated = CompletableFuture<String>()
        val path = tempDir.resolve("mcp.json")
        whenever(registry.standaloneMcpPath(AiAgent.CURSOR)).thenReturn(path)
        whenever(credentials.getCredentials(connection)).thenReturn(Either.forLeft(TokenDto("")))
        whenever(backend.generateMcpConfiguration(any(), any())).thenAnswer {
            generationRequested.countDown()
            generated
        }
        ui.tokenConfirmation = {
            consentRequested.countDown()
            check(consentRelease.await(5, TimeUnit.SECONDS))
            true
        }
        plan(McpConfigurationState.NOT_CONFIGURED, "created")
        val coordinator = coordinator(executor = pool)
        val snapshot = baseSnapshot(listOf(capability(AiAgent.CURSOR))).copy(mcpConfigurations = mapOf(
            AiAgent.CURSOR to McpAgentConfiguration(AiAgent.CURSOR, path, McpConfigurationState.NOT_CONFIGURED, emptyList())
        ))
        try {
            assertThat(coordinator.setUp(project, snapshot, AiAgent.CURSOR)).isTrue()
            assertThat(consentRequested.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(coordinator.inspectSnapshot(snapshot).get(5, TimeUnit.SECONDS).mcpConfigurations.getValue(AiAgent.CURSOR).state)
                .isEqualTo(McpConfigurationState.NOT_CONFIGURED)

            consentRelease.countDown()
            assertThat(generationRequested.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(coordinator.inspectSnapshot(snapshot).get(5, TimeUnit.SECONDS).mcpConfigurations.getValue(AiAgent.CURSOR).state)
                .isEqualTo(McpConfigurationState.NOT_CONFIGURED)

            generated.complete("generated")
            pool.shutdown()
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
            assertThat(Files.readString(path)).isEqualTo("created")
        } finally {
            consentRelease.countDown()
            generated.complete("generated")
            pool.shutdownNow()
        }
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
    fun `startup leaves a config without the IDE port field unchanged and keeps the planner diagnostic`() {
        val path = detectedFile()
        val content = """{"mcpServers":{"sonarqube":{"command":"docker","args":["sonarsource/sonarqube-mcp"],"env":{"SONARQUBE_TOKEN":"user-token"}}}}"""
        Files.writeString(path, content)
        useCorePlanner()
        val updatingCoordinator = coordinator()

        updatingCoordinator.embeddedServerStarted(64121)

        assertThat(Files.readString(path)).isEqualTo(content)
        assertThat(inspectedDiagnostics(updatingCoordinator)).anyMatch { it.contains("SONARQUBE_IDE_PORT") }
        verify(backend).planMcpConfigurationUpdate(AiAgent.CURSOR, content, portConfiguration(64121))
        verify(backend, never()).generateMcpConfiguration(any(), any())

        updatingCoordinator.embeddedServerStarted(0)

        assertThat(inspectedDiagnostics(updatingCoordinator)).noneMatch { it.contains("SONARQUBE_IDE_PORT") }
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
        val updatingCoordinator = coordinator()
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenAnswer { invocation ->
            val desired = invocation.getArgument<String>(2)
            if (desired == portConfiguration(64121)) {
                updatingCoordinator.embeddedServerStarted(64122)
            }
            if (desired == portConfiguration(64122)) {
                assertThat(Files.readString(path)).isEqualTo("existing")
            }
            CompletableFuture.completedFuture(McpConfigurationUpdatePlanResponse(McpConfigurationState.STANDALONE, desired, emptyList()))
        }

        updatingCoordinator.embeddedServerStarted(64121)

        assertThat(Files.readString(path)).isEqualTo(portConfiguration(64122))
    }

    @Test
    fun `compare and swap aborts when the file changes after planning`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenAnswer {
            Files.writeString(path, "concurrent edit")
            CompletableFuture.completedFuture(McpConfigurationUpdatePlanResponse(McpConfigurationState.NOT_CONFIGURED, "planned", emptyList()))
        }

        val result = coordinator().createConfiguration(project, AiAgent.CURSOR, path, "connection")

        assertThat(result).isEqualTo(McpTransactionResult.ConcurrentEdit)
        assertThat(Files.readString(path)).isEqualTo("concurrent edit")
    }

    @Test
    fun `a configuration symlink is read and updated through its target`() {
        val target = Files.createDirectory(tempDir.resolve("settings")).resolve("target.json")
        val link = tempDir.resolve("mcp.json")
        Files.writeString(target, "external")
        Files.createSymbolicLink(link, target)
        whenever(registry.standaloneMcpPath(AiAgent.CURSOR)).thenReturn(link)
        plan(McpConfigurationState.NOT_CONFIGURED, "updated")
        val updatingCoordinator = coordinator()

        val inspected = updatingCoordinator.inspectSnapshot(baseSnapshot(listOf(capability(AiAgent.CURSOR)))).get()

        assertThat(inspected.mcpConfigurations.getValue(AiAgent.CURSOR).path).isEqualTo(link)
        assertThat(updatingCoordinator.createConfiguration(project, AiAgent.CURSOR, link, "connection")).isEqualTo(McpTransactionResult.Updated)
        assertThat(Files.isSymbolicLink(link)).isTrue()
        assertThat(Files.readString(target)).isEqualTo("updated")
        assertThat(Files.readString(link)).isEqualTo("updated")
        Files.list(target.parent).use { assertThat(it).containsExactly(target) }
        verify(backend, atLeastOnce()).inspectMcpConfiguration(AiAgent.CURSOR, "external")
    }

    @ParameterizedTest
    @ValueSource(strings = ["relative", "absolute", "chain", "parent alias", "parent traversal"])
    fun `setup creates a missing symlink target without replacing the links`(linkType: String) {
        val target = tempDir.resolve("settings/target.json")
        val link = tempDir.resolve("mcp.json")
        val linkTarget = when (linkType) {
            "absolute" -> target
            "chain" -> {
                val intermediate = tempDir.resolve("alias.json")
                Files.createSymbolicLink(intermediate, tempDir.relativize(target))
                tempDir.relativize(intermediate)
            }
            "parent alias" -> {
                Files.createDirectories(target.parent)
                Files.createSymbolicLink(tempDir.resolve("alias"), target.parent)
                Path.of("alias/target.json")
            }
            "parent traversal" -> {
                // Windows resolves '..' before following the parent symlink.
                assumeTrue(!SystemInfo.isWindows)
                val directory = Files.createDirectories(target.parent.resolve("nested"))
                Files.createSymbolicLink(tempDir.resolve("alias"), directory)
                Path.of("alias/../target.json")
            }
            else -> tempDir.relativize(target)
        }
        Files.createSymbolicLink(link, linkTarget)
        plan(McpConfigurationState.NOT_CONFIGURED, "created with token")

        assertThat(coordinator().createConfiguration(project, AiAgent.CURSOR, link, "connection"))
            .isEqualTo(McpTransactionResult.Updated)

        assertThat(Files.readSymbolicLink(link)).isEqualTo(linkTarget)
        assertThat(Files.readString(link)).isEqualTo("created with token")
        assertThat(Files.readString(target)).isEqualTo("created with token")
        if (linkType == "chain") {
            assertThat(Files.isSymbolicLink(tempDir.resolve("alias.json"))).isTrue()
        }
        if (Files.getFileStore(target).supportsFileAttributeView(PosixFileAttributeView::class.java)) {
            assertThat(Files.getPosixFilePermissions(target)).isEqualTo(PosixFilePermissions.fromString("rw-------"))
        }
        val entries = if (linkType == "parent traversal") listOf(target, target.parent.resolve("nested")) else listOf(target)
        Files.list(target.parent).use { assertThat(it).containsExactlyInAnyOrderElementsOf(entries) }
    }

    @Test
    fun `setup rejects a symlink cycle without replacing the links`() {
        val link = tempDir.resolve("mcp.json")
        val other = tempDir.resolve("alias.json")
        Files.createSymbolicLink(link, other.fileName)
        Files.createSymbolicLink(other, link.fileName)
        plan(McpConfigurationState.NOT_CONFIGURED, "created with token")

        assertThatThrownBy { coordinator().createConfiguration(project, AiAgent.CURSOR, link, "connection") }
            .isInstanceOf(FileSystemLoopException::class.java)

        assertThat(Files.readSymbolicLink(link)).isEqualTo(other.fileName)
        assertThat(Files.readSymbolicLink(other)).isEqualTo(link.fileName)
        Files.list(tempDir).use { assertThat(it).containsExactlyInAnyOrder(link, other) }
    }

    @Test
    fun `unchanged port refresh does not rewrite the configuration`() {
        val path = detectedFile()
        plan(McpConfigurationState.STANDALONE, "existing")

        coordinator().embeddedServerStarted(64121)

        assertThat(Files.readString(path)).isEqualTo("existing")
        Files.list(tempDir).use { assertThat(it).containsExactly(path) }
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
        UIUtil.dispatchAllInvocationEvents()
        assertThat(ui.settingsOpened).isTrue()
        assertThat(ui.messages).containsExactly(RecordingMcpUi.UiMessage(
            "No SonarQube connection is available. Add a connection, then retry MCP setup.", NotificationType.ERROR
        ))
        assertSetupOutcome(AiIntegrationActionStatus.FAILED)

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

    @ParameterizedTest
    @EnumSource(value = McpConfigurationState::class, names = ["UNKNOWN", "MALFORMED", "CLI_MANAGED"])
    fun `protected MCP setup reports failure and one origin invalidation`(state: McpConfigurationState) {
        val origins = captureOrigins()
        val snapshot = setupSnapshot(state)
        assertThat(coordinator().setUp(project, snapshot, AiAgent.CURSOR)).isFalse()
        assertSetupOutcome(AiIntegrationActionStatus.FAILED)
        assertThat(origins).containsExactly(project)
    }

    @Test
    fun `missing MCP configuration reports a refresh warning and a failed attempt`() {
        val origins = captureOrigins()
        assertThat(coordinator().setUp(project, baseSnapshot(), AiAgent.CURSOR)).isFalse()
        assertSetupOutcome(AiIntegrationActionStatus.FAILED)
        assertThat(origins).containsExactly(project)
        UIUtil.dispatchAllInvocationEvents()
        assertThat(ui.messages).containsExactly(RecordingMcpUi.UiMessage(
            "MCP configuration details are unavailable. Refresh this view and try again.", NotificationType.WARNING
        ))
        assertThat(ui.settingsOpened).isFalse()
    }

    @Test
    fun `connection removed during setup reports that the selected connection no longer exists`() {
        val pending = mutableListOf<Runnable>()
        val coordinator = coordinator(java.util.concurrent.Executor { pending += it })
        assertThat(coordinator.setUp(project, setupSnapshot(), AiAgent.CURSOR)).isTrue()
        globalSettings.serverConnections = emptyList()

        pending.single().run()
        UIUtil.dispatchAllInvocationEvents()

        assertSetupOutcome(AiIntegrationActionStatus.FAILED)
        assertThat(ui.messages).containsExactly(RecordingMcpUi.UiMessage(
            "The selected SonarQube connection no longer exists. Add or select a connection, then retry.", NotificationType.ERROR
        ))
        assertThat(ui.settingsOpened).isTrue()
        verify(backend, never()).generateMcpConfiguration(any(), any())
    }

    @Test
    fun `cancelled connection selection reports one cancelled MCP attempt`() {
        val origins = captureOrigins()
        globalSettings.serverConnections = listOf(connection,
            ServerConnection.newBuilder().setName("other").setHostUrl("https://other.example").build())
        assertThat(coordinator().setUp(project, setupSnapshot(), AiAgent.CURSOR)).isFalse()
        assertSetupOutcome(AiIntegrationActionStatus.CANCELLED)
        assertThat(origins).containsExactly(project)
        verify(backend, never()).generateMcpConfiguration(any(), any())
    }

    @Test
    fun `cancelled token confirmation finishes at the real transaction result`() {
        val origins = captureOrigins()
        whenever(credentials.getCredentials(connection)).thenReturn(Either.forLeft(TokenDto("")))
        assertThat(coordinator().setUp(project, setupSnapshot(), AiAgent.CURSOR)).isTrue()
        assertSetupOutcome(AiIntegrationActionStatus.CANCELLED)
        assertThat(origins).containsExactly(project)
    }

    @Test
    fun `queued MCP transaction reports success only after the file is written`() {
        val origins = captureOrigins()
        val pending = mutableListOf<Runnable>()
        plan(McpConfigurationState.NOT_CONFIGURED, "created")
        val snapshot = setupSnapshot()
        val coordinator = coordinator(java.util.concurrent.Executor { pending += it })
        assertThat(coordinator.setUp(project, snapshot, AiAgent.CURSOR)).isTrue()
        verify(telemetry).aiIntegrationAction(AiIntegrationAction.CONFIGURE_MCP, AiIntegrationActionStatus.STARTED, AiAgent.CURSOR)
        verify(telemetry, never()).aiIntegrationAction(eq(AiIntegrationAction.CONFIGURE_MCP), eq(AiIntegrationActionStatus.SUCCEEDED), any())
        assertThat(origins).isEmpty()
        pending.removeFirst().run()
        assertSetupOutcome(AiIntegrationActionStatus.SUCCEEDED)
        assertThat(Files.readString(snapshot.mcpConfigurations.getValue(AiAgent.CURSOR).path)).isEqualTo("created")
        assertThat(origins.filterNotNull()).containsExactly(project)
    }

    @Test
    fun `MCP transaction failure and executor rejection finish accepted attempts`() {
        val origins = captureOrigins()
        val coordinator = coordinator(java.util.concurrent.Executor { throw java.util.concurrent.RejectedExecutionException() })
        assertThat(coordinator.setUp(project, setupSnapshot(), AiAgent.CURSOR)).isFalse()
        assertSetupOutcome(AiIntegrationActionStatus.FAILED)
        assertThat(origins).containsExactly(project)
    }

    @Test
    fun `automatic port maintenance emits no action telemetry and only background invalidation`() {
        val origins = captureOrigins()
        coordinator().embeddedServerStarted(64121)
        verifyNoInteractions(telemetry)
        assertThat(origins).containsExactly(null)
    }

    @Test
    fun `file opening reports observable editor results without state invalidation`() {
        val origins = captureOrigins()
        val coordinator = coordinator()
        coordinator.openConfiguration(project, AiAgent.CURSOR, setupSnapshot())
        verify(telemetry).aiIntegrationAction(AiIntegrationAction.OPEN_MCP_CONFIGURATION, AiIntegrationActionStatus.SUCCEEDED, AiAgent.CURSOR)
        coordinator.openConfiguration(project, AiAgent.CLAUDE_CODE, baseSnapshot())
        verify(telemetry).aiIntegrationAction(AiIntegrationAction.OPEN_MCP_CONFIGURATION, AiIntegrationActionStatus.FAILED, AiAgent.CLAUDE_CODE)
        assertThat(origins).isEmpty()
    }

    private fun setupSnapshot(state: McpConfigurationState = McpConfigurationState.NOT_CONFIGURED) = baseSnapshot().copy(mcpConfigurations = mapOf(
        AiAgent.CURSOR to McpAgentConfiguration(AiAgent.CURSOR, tempDir.resolve("telemetry-mcp.json"), state, emptyList())
    ))

    private fun captureOrigins(): MutableList<Project?> {
        val origins = mutableListOf<Project?>()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(AiIntegrationListener.TOPIC, AiIntegrationListener { origins += it })
        return origins
    }

    private fun assertSetupOutcome(expected: AiIntegrationActionStatus) {
        val statuses = argumentCaptor<AiIntegrationActionStatus>()
        verify(telemetry, times(2)).aiIntegrationAction(eq(AiIntegrationAction.CONFIGURE_MCP), statuses.capture(), eq(AiAgent.CURSOR))
        assertThat(statuses.allValues).containsExactly(AiIntegrationActionStatus.STARTED, expected)
    }

    private fun coordinator(
        executor: java.util.concurrent.Executor = java.util.concurrent.Executor { it.run() }
    ) = McpConfigurationCoordinator(backend, registry, credentials, ui, executor)

    private fun inspectedDiagnostics(updatingCoordinator: McpConfigurationCoordinator): List<String> =
        updatingCoordinator.inspectSnapshot(baseSnapshot(listOf(capability(AiAgent.CURSOR)))).get()
            .mcpConfigurations.getValue(AiAgent.CURSOR).diagnostics

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
    var tokenConfirmation: () -> Boolean = { proceedWithoutToken }
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
        return tokenConfirmation()
    }

    override fun openConfiguration(project: com.intellij.openapi.project.Project, path: Path) = true

    override fun openConnectionSettings(project: com.intellij.openapi.project.Project) {
        settingsOpened = true
    }

    override fun showMessage(project: com.intellij.openapi.project.Project, message: String, type: NotificationType) {
        messages += UiMessage(message, type)
    }

    data class UiMessage(val message: String, val type: NotificationType)
}
