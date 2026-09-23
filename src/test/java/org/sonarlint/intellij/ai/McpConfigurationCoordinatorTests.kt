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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto

class McpConfigurationCoordinatorTests : AbstractSonarLintLightTests() {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var backend: BackendService
    private lateinit var credentials: CredentialsService
    private lateinit var ownership: McpConfigurationOwnership
    private lateinit var ui: RecordingMcpUi
    private val connection = ServerConnection.newBuilder()
        .setName("connection")
        .setHostUrl("https://sonar.example")
        .build()

    @BeforeEach
    fun prepare() {
        backend = mock()
        credentials = mock()
        ownership = McpConfigurationOwnership()
        ui = RecordingMcpUi()
        globalSettings.serverConnections = listOf(connection)
        whenever(credentials.getCredentials(connection)).thenReturn(Either.forLeft(TokenDto("secret-token")))
        whenever(backend.generateMcpConfiguration(eq("connection"), any())).thenReturn(CompletableFuture.completedFuture("{\"sonar\":true}"))
    }

    @Test
    fun `capability and known path must both be present and all core states are retained`() {
        val registry = mock<AiAgentRegistry>()
        val cursorPath = tempDir.resolve("cursor.json")
        val claudePath = tempDir.resolve("claude.json")
        whenever(registry.standaloneMcpPath(AiAgentId.CURSOR)).thenReturn(cursorPath)
        whenever(registry.standaloneMcpPath(AiAgentId.CLAUDE_CODE)).thenReturn(claudePath)
        whenever(backend.inspectMcpConfiguration(any(), any())).thenAnswer { invocation ->
            val agent = invocation.getArgument<AiAgentId>(0)
            CompletableFuture.completedFuture(
                McpInspection(
                    if (agent == AiAgentId.CURSOR) McpConfigurationKind.MALFORMED else McpConfigurationKind.CLI_MANAGED,
                    listOf("diagnostic")
                )
            )
        }
        val snapshot = baseSnapshot(
            listOf(
                capability(AiAgentId.CURSOR, standalone = true),
                capability(AiAgentId.CLAUDE_CODE, standalone = true),
                capability(AiAgentId.GITHUB_COPILOT, standalone = true),
                capability(AiAgentId.KIRO, standalone = false)
            )
        )

        val inspected = coordinator(registry = registry).inspectSnapshot(snapshot).get()

        assertThat(inspected.mcpConfigurations.keys).containsExactlyInAnyOrder(
            AiAgentId.CURSOR,
            AiAgentId.CLAUDE_CODE,
            AiAgentId.GITHUB_COPILOT,
            AiAgentId.KIRO
        )
        assertThat(inspected.mcpConfigurations[AiAgentId.CURSOR]?.state).isEqualTo(McpConfigurationKind.MALFORMED)
        assertThat(inspected.mcpConfigurations[AiAgentId.CLAUDE_CODE]?.state).isEqualTo(McpConfigurationKind.CLI_MANAGED)
        assertThat(inspected.mcpConfigurations[AiAgentId.GITHUB_COPILOT]?.state).isEqualTo(McpConfigurationKind.CLI_ONLY)
        assertThat(inspected.mcpConfigurations[AiAgentId.KIRO]?.state).isEqualTo(McpConfigurationKind.CLI_ONLY)
    }

    @Test
    fun `transaction preserves unrelated content creates backup and records ownership after success`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "{\"unrelated\":true}")
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.NOT_CONFIGURED, emptyList()))
        )
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenReturn(
            CompletableFuture.completedFuture(
                McpUpdatePlan(McpConfigurationKind.STANDALONE, "{\"unrelated\":true,\"sonar\":true}", emptyList())
            )
        )

        val result = coordinator().safeUpdate(AiAgentId.CURSOR, path, "connection", false)

        assertThat(result).isEqualTo(McpTransactionResult.Updated)
        assertThat(Files.readString(path)).contains("\"unrelated\":true").contains("\"sonar\":true")
        assertThat(Files.readString(tempDir.resolve("mcp.json.bak"))).isEqualTo("{\"unrelated\":true}")
        assertThat(ownership.connectionId(AiAgentId.CURSOR)).isEqualTo("connection")
        assertThat(ownership.record(AiAgentId.CURSOR)?.fingerprint)
            .isEqualTo(managedFingerprint(Files.readAllBytes(path)))
    }

    @Test
    fun `compare and swap aborts when the file changes after planning`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.NOT_CONFIGURED, emptyList()))
        )
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenAnswer {
            Files.writeString(path, "concurrent edit")
            CompletableFuture.completedFuture(McpUpdatePlan(McpConfigurationKind.STANDALONE, "planned", emptyList()))
        }

        val result = coordinator().safeUpdate(AiAgentId.CURSOR, path, "connection", false)

        assertThat(result).isEqualTo(McpTransactionResult.ConcurrentEdit)
        assertThat(Files.readString(path)).isEqualTo("concurrent edit")
        assertThat(ownership.connectionId(AiAgentId.CURSOR)).isNull()
        assertThat(Files.exists(tempDir.resolve("mcp.json.bak"))).isFalse()
    }

    @Test
    fun `compare and swap rechecks after backup and temp creation and cleans both`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.NOT_CONFIGURED, emptyList()))
        )
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpUpdatePlan(McpConfigurationKind.STANDALONE, "planned", emptyList()))
        )
        val fileSystem = MutatingAfterTempFileSystem(path)

        val result = coordinator(fileSystem = fileSystem).safeUpdate(AiAgentId.CURSOR, path, "connection", false)

        assertThat(result).isEqualTo(McpTransactionResult.ConcurrentEdit)
        assertThat(Files.readString(path)).isEqualTo("changed after temp creation")
        assertThat(fileSystem.temp).isNotNull()
        assertThat(Files.exists(fileSystem.temp!!)).isFalse()
        assertThat(Files.exists(tempDir.resolve("mcp.json.bak"))).isFalse()
        assertThat(ownership.record(AiAgentId.CURSOR)).isNull()
    }

    @Test
    fun `external standalone requires takeover while protected states remain immutable`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "external")
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.STANDALONE, emptyList()))
        )
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpUpdatePlan(McpConfigurationKind.STANDALONE, "updated", emptyList()))
        )
        val coordinator = coordinator()

        assertThat(coordinator.safeUpdate(AiAgentId.CURSOR, path, "connection", false))
            .isEqualTo(McpTransactionResult.Protected)
        assertThat(coordinator.safeUpdate(AiAgentId.CURSOR, path, "connection", true))
            .isEqualTo(McpTransactionResult.Updated)

        McpConfigurationKind.entries.filter { it == McpConfigurationKind.CLI_MANAGED || it == McpConfigurationKind.UNKNOWN || it == McpConfigurationKind.MALFORMED }
            .forEach { state ->
                whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
                    CompletableFuture.completedFuture(McpInspection(state, emptyList()))
                )
                assertThat(coordinator.safeUpdate(AiAgentId.CURSOR, path, "connection", true))
                    .isEqualTo(McpTransactionResult.Protected)
            }
    }

    @Test
    fun `username password credentials are rejected without generating or disclosing configuration`() {
        whenever(credentials.getCredentials(connection)).thenReturn(
            Either.forRight(org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto("user", "password"))
        )
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.NOT_CONFIGURED, emptyList()))
        )

        val result = coordinator().safeUpdate(AiAgentId.CURSOR, tempDir.resolve("mcp.json"), "connection", false)

        assertThat(result).isEqualTo(McpTransactionResult.InvalidCredentials)
        verify(backend, never()).generateMcpConfiguration(any(), any())
    }

    @Test
    fun `symbolic link is refused before configuration content is inspected`() {
        val target = tempDir.resolve("target.json")
        val link = tempDir.resolve("mcp.json")
        Files.writeString(target, "external")
        Files.createSymbolicLink(link, target)

        val result = coordinator().safeUpdate(AiAgentId.CURSOR, link, "connection", true)

        assertThat(result).isEqualTo(McpTransactionResult.SymlinkRefused)
        assertThat(Files.readString(target)).isEqualTo("external")
        verify(backend, never()).inspectMcpConfiguration(any(), any())
    }

    @Test
    fun `unchanged explicit takeover records verifiable ownership`() {
        val path = tempDir.resolve("mcp.json")
        val content = "{\"sonar\":true}"
        Files.writeString(path, content)
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.STANDALONE, emptyList()))
        )
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpUpdatePlan(McpConfigurationKind.STANDALONE, content, emptyList()))
        )

        val result = coordinator().safeUpdate(AiAgentId.CURSOR, path, "connection", true)

        assertThat(result).isEqualTo(McpTransactionResult.Unchanged)
        assertThat(ownership.record(AiAgentId.CURSOR)).isEqualTo(
            ManagedMcpOwnership("connection", managedFingerprint(content.toByteArray()))
        )
    }

    @Test
    fun `fingerprint mismatch prevents automatic refresh and clears stale ownership`() {
        val registry = mock<AiAgentRegistry>()
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "changed externally")
        whenever(registry.standaloneMcpPath(AiAgentId.CURSOR)).thenReturn(path)
        ownership.remember(AiAgentId.CURSOR, "connection", managedFingerprint("previous".toByteArray()))

        coordinator(registry = registry).backendReady()

        assertThat(ownership.record(AiAgentId.CURSOR)).isNull()
        verify(backend, never()).inspectMcpConfiguration(any(), any())
        verify(backend, never()).planMcpConfigurationUpdate(any(), any(), any())
    }

    @Test
    fun `missing persisted connection becomes setup-again state instead of owned`() {
        val registry = mock<AiAgentRegistry>()
        val path = tempDir.resolve("mcp.json")
        val content = "managed"
        Files.writeString(path, content)
        whenever(registry.standaloneMcpPath(AiAgentId.CURSOR)).thenReturn(path)
        whenever(backend.inspectMcpConfiguration(AiAgentId.CURSOR, content)).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.STANDALONE, emptyList()))
        )
        ownership.remember(AiAgentId.CURSOR, "removed", managedFingerprint(content.toByteArray()))
        val snapshot = baseSnapshot(listOf(capability(AiAgentId.CURSOR, standalone = true)))

        val inspected = coordinator(registry = registry).inspectSnapshot(snapshot).get()

        assertThat(inspected.mcpConfigurations.getValue(AiAgentId.CURSOR).owned).isFalse()
        assertThat(inspected.mcpConfigurations.getValue(AiAgentId.CURSOR).diagnostics)
            .anyMatch { it.contains("no longer exists") }
        assertThat(ownership.record(AiAgentId.CURSOR)).isNull()
    }

    @Test
    fun `serialized inspection cannot clear ownership established after it started`() {
        val registry = mock<AiAgentRegistry>()
        val path = tempDir.resolve("mcp.json")
        val content = "managed"
        Files.writeString(path, content)
        whenever(registry.standaloneMcpPath(AiAgentId.CURSOR)).thenReturn(path)
        ownership.remember(AiAgentId.CURSOR, "connection", managedFingerprint("stale".toByteArray()))
        val inspectionStarted = CountDownLatch(1)
        val inspectionResult = CompletableFuture<McpInspection>()
        whenever(backend.inspectMcpConfiguration(AiAgentId.CURSOR, content)).thenAnswer {
            inspectionStarted.countDown()
            inspectionResult
        }
        val pool = Executors.newFixedThreadPool(2)
        val coordinator = coordinator(registry = registry, executor = pool)

        val inspection = coordinator.inspectSnapshot(baseSnapshot(listOf(capability(AiAgentId.CURSOR, standalone = true))))
        assertThat(inspectionStarted.await(5, TimeUnit.SECONDS)).isTrue()
        val setup = coordinator.executeSerialized(path) {
            ownership.remember(AiAgentId.CURSOR, "connection", managedFingerprint("fresh".toByteArray()))
            McpTransactionResult.Unchanged
        }
        inspectionResult.complete(McpInspection(McpConfigurationKind.STANDALONE, emptyList()))
        CompletableFuture.allOf(inspection, setup).get(5, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertThat(ownership.record(AiAgentId.CURSOR))
            .isEqualTo(ManagedMcpOwnership("connection", managedFingerprint("fresh".toByteArray())))
    }

    @Test
    fun `same normalized path is serialized`() {
        val pool = Executors.newFixedThreadPool(2)
        val coordinator = coordinator(executor = pool)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
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
        pool.shutdownNow()

        assertThat(maxActive.get()).isEqualTo(1)
    }

    @Test
    fun `failed final move cleans the sibling temp and does not claim ownership`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "before")
        whenever(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.NOT_CONFIGURED, emptyList()))
        )
        whenever(backend.planMcpConfigurationUpdate(any(), any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpUpdatePlan(McpConfigurationKind.STANDALONE, "after", emptyList()))
        )
        val fileSystem = FailingReplaceFileSystem()

        assertThatThrownBy {
            coordinator(fileSystem = fileSystem).safeUpdate(AiAgentId.CURSOR, path, "connection", false)
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(fileSystem.temp).isNotNull()
        assertThat(Files.exists(fileSystem.temp!!)).isFalse()
        assertThat(Files.exists(tempDir.resolve("mcp.json.bak"))).isFalse()
        assertThat(ownership.connectionId(AiAgentId.CURSOR)).isNull()
        assertThat(Files.readString(path)).isEqualTo("before")
    }

    @Test
    fun `missing MCP connection opens settings and external takeover is explicit`() {
        val path = tempDir.resolve("mcp.json")
        Files.writeString(path, "external")
        val missingSnapshot = baseSnapshot(emptyList()).copy(
            connectionChoices = emptyList(),
            mcpConfigurations = mapOf(
                AiAgentId.CURSOR to McpAgentConfiguration(
                    AiAgentId.CURSOR,
                    path,
                    McpConfigurationKind.NOT_CONFIGURED,
                    false,
                    emptyList()
                )
            )
        )
        val coordinator = coordinator()

        assertThat(coordinator.setUp(project, missingSnapshot, AiAgentId.CURSOR)).isFalse()
        assertThat(ui.settingsOpened).isTrue()
        assertThat(ui.messages.last().message).contains("No SonarQube connection")

        val externalSnapshot = baseSnapshot(emptyList()).copy(
            mcpConfigurations = mapOf(
                AiAgentId.CURSOR to McpAgentConfiguration(
                    AiAgentId.CURSOR,
                    path,
                    McpConfigurationKind.STANDALONE,
                    false,
                    emptyList()
                )
            )
        )
        assertThat(coordinator.setUp(project, externalSnapshot, AiAgentId.CURSOR)).isFalse()
        assertThat(ui.takeoverRequests).isEqualTo(1)
        assertThat(ui.messages.last().message).contains("cancelled")
    }

    @Test
    fun `every setup result and unexpected failure has actionable feedback`() {
        val coordinator = coordinator()

        McpTransactionResult.entries.forEach { result ->
            coordinator.reportSetupResult(project, result, null)
        }
        coordinator.reportSetupResult(project, null, IllegalStateException("planning failed"))

        assertThat(ui.messages).hasSize(McpTransactionResult.entries.size + 1)
        assertThat(ui.messages.map { it.message }).allSatisfy { message ->
            assertThat(message).isNotBlank()
            assertThat(message).matches("(?is).*(retry|restart|reload|managed|refresh|review|update|open|replace).*")
        }
        assertThat(ui.messages.map { it.type }).contains(NotificationType.ERROR, NotificationType.WARNING)
        assertThat(ui.settingsOpened).isTrue()
    }

    @Test
    fun `backend refresh includes undetected owned agents and clears ownership when removed or CLI managed`() {
        val registry = mock<AiAgentRegistry>()
        val path = tempDir.resolve("claude.json")
        whenever(registry.standaloneMcpPath(AiAgentId.CLAUDE_CODE)).thenReturn(path)
        ownership.remember(AiAgentId.CLAUDE_CODE, "connection", managedFingerprint("missing".toByteArray()))
        val coordinator = coordinator(registry = registry)

        coordinator.backendReady()
        assertThat(ownership.connectionId(AiAgentId.CLAUDE_CODE)).isNull()

        Files.writeString(path, "managed")
        ownership.remember(AiAgentId.CLAUDE_CODE, "connection", managedFingerprint("managed".toByteArray()))
        whenever(backend.inspectMcpConfiguration(AiAgentId.CLAUDE_CODE, "managed")).thenReturn(
            CompletableFuture.completedFuture(McpInspection(McpConfigurationKind.CLI_MANAGED, emptyList()))
        )
        coordinator.backendReady()
        assertThat(ownership.connectionId(AiAgentId.CLAUDE_CODE)).isNull()
    }

    private fun coordinator(
        registry: AiAgentRegistry = mock(),
        fileSystem: McpFileSystem = NioMcpFileSystem(),
        executor: java.util.concurrent.Executor = java.util.concurrent.Executor { it.run() }
    ) = McpConfigurationCoordinator(
        backend,
        registry,
        ownership,
        credentials,
        fileSystem,
        ui,
        AiIntegrationEnvironment { false },
        executor,
        false
    )

    private fun baseSnapshot(agents: List<AgentCapability>) = AiIntegrationSnapshot(
        CliState(CliInstallationState.INSTALLED, CliAuthenticationState.AUTHENTICATED, null, null, null),
        agents,
        listOf(IntegrationConnection("connection", "https://sonar.example", null)),
        null
    )

    private fun capability(agent: AiAgentId, standalone: Boolean) =
        AgentCapability(agent, emptySet(), cliIntegrationSupported = false, standaloneMcpSupported = standalone)
}

private class RecordingMcpUi : McpUiAdapter {
    var settingsOpened = false
    var takeoverConfirmed = false
    var takeoverRequests = 0
    val messages = mutableListOf<UiMessage>()

    override fun chooseConnection(project: com.intellij.openapi.project.Project, connections: List<IntegrationConnection>): String? = null

    override fun confirmExternalTakeover(project: com.intellij.openapi.project.Project): Boolean {
        takeoverRequests++
        return takeoverConfirmed
    }

    override fun openConfiguration(project: com.intellij.openapi.project.Project, path: Path) = Unit

    override fun openConnectionSettings(project: com.intellij.openapi.project.Project) {
        settingsOpened = true
    }

    override fun showMessage(
        project: com.intellij.openapi.project.Project,
        message: String,
        type: NotificationType
    ) {
        messages += UiMessage(message, type)
    }

    data class UiMessage(val message: String, val type: NotificationType)
}

private class FailingReplaceFileSystem : McpFileSystem {
    private val delegate = NioMcpFileSystem()
    var temp: Path? = null

    override fun read(path: Path): ByteArray? = delegate.read(path)

    override fun createBackup(path: Path, content: ByteArray): Path? = delegate.createBackup(path, content)

    override fun writeSiblingTemp(path: Path, content: ByteArray): Path =
        delegate.writeSiblingTemp(path, content).also { temp = it }

    override fun replace(temp: Path, target: Path) {
        throw IllegalStateException("move failed")
    }

    override fun deleteIfExists(path: Path) = delegate.deleteIfExists(path)

    override fun isSymbolicLink(path: Path): Boolean = delegate.isSymbolicLink(path)
}

private class MutatingAfterTempFileSystem(private val target: Path) : McpFileSystem {
    private val delegate = NioMcpFileSystem()
    var temp: Path? = null

    override fun read(path: Path): ByteArray? = delegate.read(path)

    override fun createBackup(path: Path, content: ByteArray): Path? = delegate.createBackup(path, content)

    override fun writeSiblingTemp(path: Path, content: ByteArray): Path =
        delegate.writeSiblingTemp(path, content).also {
            temp = it
            Files.writeString(target, "changed after temp creation")
        }

    override fun replace(temp: Path, target: Path) = delegate.replace(temp, target)

    override fun deleteIfExists(path: Path) = delegate.deleteIfExists(path)

    override fun isSymbolicLink(path: Path): Boolean = delegate.isSymbolicLink(path)
}
