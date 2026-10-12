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
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.FileSystemLoopException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState

@Service(Service.Level.APP)
class McpConfigurationCoordinator @JvmOverloads constructor(
    private val backendService: BackendService = getService(BackendService::class.java),
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val credentialsService: CredentialsService = getService(CredentialsService::class.java),
    private val ui: McpUiAdapter = IntellijMcpUiAdapter(),
    private val executor: Executor = AppExecutorUtil.getAppExecutorService()
) {
    private val idePort = AtomicInteger()
    private val refreshRequested = AtomicBoolean()
    private val refreshWorkerRunning = AtomicBoolean()
    private val refreshDiagnostics = ConcurrentHashMap<AiAgent, List<String>>()

    fun inspectSnapshot(snapshot: AiIntegrationSnapshot): CompletableFuture<AiIntegrationSnapshot> {
        val inspections = snapshot.agents.mapNotNull { capability ->
            if (!capability.standaloneMcpSupported) {
                return@mapNotNull null
            }
            val path = registry.standaloneMcpPath(capability.agent) ?: return@mapNotNull null
            CompletableFuture.supplyAsync({
                val inspection = backendService.inspectMcpConfiguration(capability.agent, readConfig(path) ?: "").join()
                val diagnostics = (inspection.diagnostics + rememberedDiagnostics(capability.agent)).distinct()
                McpAgentConfiguration(capability.agent, path, inspection.state, diagnostics)
            }, executor).exceptionally { error ->
                val detail = error.cause?.message ?: error.message
                McpAgentConfiguration(
                    capability.agent,
                    path,
                    McpConfigurationState.UNKNOWN,
                    listOf(if (detail.isNullOrBlank()) "Unable to inspect the MCP configuration." else "Unable to inspect the MCP configuration: $detail")
                )
            }
        }
        return CompletableFuture.allOf(*inspections.toTypedArray()).thenApply {
            snapshot.copy(mcpConfigurations = inspections.associate { future ->
                val configuration = future.join()
                configuration.agent to configuration
            })
        }
    }

    fun setUp(project: Project, snapshot: AiIntegrationSnapshot, agent: AiAgent): Boolean {
        val configuration = snapshot.mcpConfigurations[agent]
        if (configuration == null) {
            ui.showMessage(project, "MCP configuration details are unavailable. Refresh this view and try again.", NotificationType.WARNING)
            return false
        }
        val path = configuration.path
        if (configuration.state != McpConfigurationState.NOT_CONFIGURED && configuration.state != McpConfigurationState.STANDALONE) {
            reportSetupResult(project, McpTransactionResult.Protected, null)
            return false
        }
        val connectionId = if (configuration.state == McpConfigurationState.NOT_CONFIGURED) {
            val connections = getGlobalSettings().serverConnections.map { connection ->
                IntegrationConnection(connection.name, connection.hostUrl, connection.organizationKey)
            }
            when (val selection = ConnectionSelector(ui::chooseConnection).select(project, connections, snapshot.recommendedConnectionId)) {
                is ConnectionSelection.Selected -> selection.connectionId
                ConnectionSelection.Missing -> {
                    ui.showMessage(project, "No SonarQube connection is available. Add a connection, then retry MCP setup.", NotificationType.ERROR)
                    ui.openConnectionSettings(project)
                    return false
                }
                ConnectionSelection.Cancelled -> {
                    reportSetupResult(project, McpTransactionResult.Cancelled, null)
                    return false
                }
            }
        } else {
            null
        }
        CompletableFuture.supplyAsync({
            if (connectionId == null) refreshPort(agent, path) else createConfiguration(project, agent, path, connectionId)
        }, executor).whenComplete { result, error ->
            runOnUiThread(project) {
                reportSetupResult(project, result, error)
                refreshAll()
            }
        }
        return true
    }

    fun openConfiguration(project: Project, agent: AiAgent, snapshot: AiIntegrationSnapshot) {
        snapshot.mcpConfigurations[agent]?.path?.let { ui.openConfiguration(project, it) }
    }

    fun embeddedServerStarted(port: Int) {
        if (port !in 1..65535) {
            idePort.set(0)
            clearRefreshDiagnostics()
            return
        }
        idePort.set(port)
        requestRefresh()
    }

    private fun requestRefresh() {
        if (idePort.get() == 0) {
            return
        }
        refreshRequested.set(true)
        if (refreshWorkerRunning.compareAndSet(false, true)) {
            CompletableFuture.runAsync(::drainPortRefreshes, executor)
        }
    }

    private fun drainPortRefreshes() {
        try {
            while (refreshRequested.getAndSet(false)) {
                val port = idePort.get()
                val snapshot = backendService.getAiIntegrationState(null, registry.detectedIdeAgents()).join()
                snapshot.agents.filter { it.standaloneMcpSupported }.forEach { capability ->
                    try {
                        registry.standaloneMcpPath(capability.agent)?.let { path ->
                            refreshPort(capability.agent, path, port)
                        }
                    } catch (error: Throwable) {
                        GlobalLogOutput.get().logError("Unable to refresh the MCP configuration for ${capability.agent}", error)
                    }
                }
            }
        } catch (error: Throwable) {
            GlobalLogOutput.get().logError("Unable to discover AI agents for MCP port refresh", error)
        } finally {
            refreshWorkerRunning.set(false)
            refreshAll()
            if (refreshRequested.get()) {
                requestRefresh()
            }
        }
    }

    internal fun createConfiguration(project: Project, agent: AiAgent, path: Path, connectionId: String): McpTransactionResult {
        val inspection = backendService.inspectMcpConfiguration(agent, readConfig(path) ?: "").join()
        if (inspection.state != McpConfigurationState.NOT_CONFIGURED) {
            return refreshPort(agent, path)
        }
        val connection = getGlobalSettings().serverConnections.firstOrNull { it.name == connectionId }
            ?: return McpTransactionResult.MissingConnection
        val credentials = credentialsService.getCredentials(connection)
        val token = credentials.takeIf { it.isLeft }?.left?.token?.takeIf { it.isNotBlank() } ?: ""
        if (token.isEmpty() && !ui.confirmWithoutToken(project)) {
            return McpTransactionResult.Cancelled
        }
        val generated = backendService.generateMcpConfiguration(connectionId, token).join()
        val result = updateConfiguration(agent, path, generated, McpConfigurationState.NOT_CONFIGURED)
        if (result == McpTransactionResult.Updated || result == McpTransactionResult.Unchanged) {
            requestRefresh()
        }
        return result
    }

    private fun refreshPort(agent: AiAgent, path: Path, port: Int = idePort.get()): McpTransactionResult {
        if (port !in 1..65535) {
            refreshDiagnostics.remove(agent)
            return McpTransactionResult.Protected
        }
        return updateConfiguration(agent, path, """{"env":{"SONARQUBE_IDE_PORT":"$port"}}""", McpConfigurationState.STANDALONE) {
            idePort.get() == port
        }
    }

    private fun updateConfiguration(
        agent: AiAgent,
        path: Path,
        desired: String,
        expectedState: McpConfigurationState,
        isCurrent: () -> Boolean = { true }
    ): McpTransactionResult {
        val normalizedPath = path.toAbsolutePath().normalize()
        val snapshot = readConfig(normalizedPath)
        if (expectedState == McpConfigurationState.STANDALONE && snapshot == null) {
            return McpTransactionResult.Protected
        }
        val plan = backendService.planMcpConfigurationUpdate(agent, snapshot ?: "", desired).join()
        if (plan.state != expectedState) {
            return McpTransactionResult.Protected
        }
        val updated = plan.updatedContent
        if (updated == null) {
            rememberPlanDiagnostics(agent, plan.diagnostics)
            return McpTransactionResult.Protected
        }
        refreshDiagnostics.remove(agent)
        if (updated == snapshot) {
            return McpTransactionResult.Unchanged
        }
        if (readConfig(normalizedPath) != snapshot) {
            return McpTransactionResult.ConcurrentEdit
        }
        if (!isCurrent()) {
            return McpTransactionResult.Protected
        }
        val target = resolveConfigTarget(normalizedPath)
        Files.createDirectories(target.parent)
        val posix = Files.getFileStore(target.parent).supportsFileAttributeView(PosixFileAttributeView::class.java)
        val attributes = if (posix) arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) else emptyArray()
        val temporary = Files.createTempFile(target.parent, ".sonarqube-mcp-", ".tmp", *attributes)
        try {
            Files.writeString(temporary, updated)
            if (snapshot != null) {
                preservePermissions(target, temporary, posix)
            }
            if (readConfig(normalizedPath) != snapshot || resolveConfigTarget(normalizedPath) != target) {
                return McpTransactionResult.ConcurrentEdit
            }
            if (!isCurrent()) {
                return McpTransactionResult.Protected
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return McpTransactionResult.Updated
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun resolveConfigTarget(path: Path): Path {
        var target = path
        val visited = mutableSetOf<Path>()
        while (Files.isSymbolicLink(target)) {
            val link = target.parent.toRealPath().resolve(target.fileName)
            if (!visited.add(link)) {
                throw FileSystemLoopException(link.toString())
            }
            target = link.parent.resolve(Files.readSymbolicLink(link))
        }
        return if (Files.exists(target)) target.toRealPath() else target
    }

    private fun preservePermissions(source: Path, temporary: Path, posix: Boolean) {
        if (posix) {
            val original = Files.getFileAttributeView(source, PosixFileAttributeView::class.java).readAttributes()
            Files.getFileAttributeView(temporary, PosixFileAttributeView::class.java).apply {
                if (owner != original.owner()) owner = original.owner()
                if (readAttributes().group() != original.group()) setGroup(original.group())
                setPermissions(original.permissions())
            }
        } else {
            Files.getFileAttributeView(source, AclFileAttributeView::class.java)?.let { original ->
                Files.getFileAttributeView(temporary, AclFileAttributeView::class.java).apply {
                    if (owner != original.owner) owner = original.owner
                    acl = original.acl
                }
            }
        }
    }

    private fun rememberedDiagnostics(agent: AiAgent): List<String> {
        if (idePort.get() == 0) {
            return emptyList()
        }
        return refreshDiagnostics[agent].orEmpty()
    }

    private fun rememberPlanDiagnostics(agent: AiAgent, diagnostics: List<String>) {
        if (idePort.get() == 0 || diagnostics.isEmpty()) {
            refreshDiagnostics.remove(agent)
            return
        }
        refreshDiagnostics[agent] = diagnostics
        if (idePort.get() == 0) {
            refreshDiagnostics.remove(agent)
        }
    }

    private fun clearRefreshDiagnostics() {
        if (refreshDiagnostics.isEmpty()) {
            return
        }
        refreshDiagnostics.clear()
        refreshAll()
    }

    private fun readConfig(path: Path): String? = if (Files.exists(path)) Files.readString(path) else null

    internal fun reportSetupResult(project: Project, result: McpTransactionResult?, error: Throwable?) {
        if (error != null) {
            ui.showMessage(
                project,
                "MCP setup failed. Check the configuration file permissions and connection settings, then retry.",
                NotificationType.ERROR
            )
            return
        }
        val (message, type) = when (result) {
            McpTransactionResult.Updated ->
                "MCP configuration updated. Restart or reload the AI agent to use the updated settings." to NotificationType.INFORMATION
            McpTransactionResult.Unchanged ->
                "MCP configuration is already up to date." to NotificationType.INFORMATION
            McpTransactionResult.ConcurrentEdit ->
                "The MCP configuration changed during setup. Review the file and retry so no edits are lost." to NotificationType.WARNING
            McpTransactionResult.Protected ->
                "The MCP configuration was not changed because its current state cannot be updated safely. Open the file, resolve the issue, and retry." to NotificationType.WARNING
            McpTransactionResult.MissingConnection ->
                "The selected SonarQube connection no longer exists. Add or select a connection, then retry." to NotificationType.ERROR
            McpTransactionResult.Cancelled ->
                "MCP setup was cancelled. Choose Set up when you are ready to continue." to NotificationType.INFORMATION
            null ->
                "MCP setup did not return a result. Refresh this view and retry." to NotificationType.WARNING
        }
        ui.showMessage(project, message, type)
        if (result == McpTransactionResult.MissingConnection) {
            ui.openConnectionSettings(project)
        }
    }

    private fun refreshAll() {
        runOnUiThread(ModalityState.defaultModalityState()) {
            ApplicationManager.getApplication().messageBus.syncPublisher(AiIntegrationListener.TOPIC).stateChanged()
        }
    }
}

enum class McpTransactionResult {
    Updated,
    Unchanged,
    ConcurrentEdit,
    Protected,
    MissingConnection,
    Cancelled
}
