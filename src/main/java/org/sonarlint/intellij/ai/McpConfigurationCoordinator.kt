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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent

@Service(Service.Level.APP)
class McpConfigurationCoordinator @JvmOverloads constructor(
    private val backendService: BackendService = getService(BackendService::class.java),
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val credentialsService: CredentialsService = getService(CredentialsService::class.java),
    private val fileSystem: McpFileSystem = NioMcpFileSystem(),
    private val ui: McpUiAdapter = IntellijMcpUiAdapter(),
    private val executor: Executor = AppExecutorUtil.getAppExecutorService()
) : Disposable {
    private val pathLocks = ConcurrentHashMap<Path, ReentrantLock>()
    private val refreshCallbacks = ConcurrentHashMap<Any, () -> Unit>()
    private val idePort = AtomicInteger()
    private val refreshRequested = AtomicBoolean()
    private val refreshWorkerRunning = AtomicBoolean()

    fun register(owner: Any, refresh: () -> Unit) {
        refreshCallbacks[owner] = refresh
    }

    fun unregister(owner: Any) {
        refreshCallbacks.remove(owner)
    }

    fun inspectSnapshot(snapshot: AiIntegrationSnapshot): CompletableFuture<AiIntegrationSnapshot> {
        val inspections = snapshot.agents.mapNotNull { capability ->
            if (!capability.standaloneMcpSupported) {
                return@mapNotNull null
            }
            val path = registry.standaloneMcpPath(capability.agent) ?: return@mapNotNull null
            CompletableFuture.supplyAsync({
                runSerialized(path) {
                    val inspection = backendService.inspectMcpConfiguration(capability.agent, fileSystem.read(path).toText()).join()
                    McpAgentConfiguration(capability.agent, path, inspection.state, inspection.diagnostics)
                }
            }, executor).exceptionally { error ->
                val detail = error.cause?.message ?: error.message
                McpAgentConfiguration(
                    capability.agent,
                    path,
                    McpConfigurationKind.UNKNOWN,
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
        if (configuration.state != McpConfigurationKind.NOT_CONFIGURED && configuration.state != McpConfigurationKind.STANDALONE) {
            reportSetupResult(project, McpTransactionResult.Protected, null)
            return false
        }
        val connectionId = if (configuration.state == McpConfigurationKind.NOT_CONFIGURED) {
            when (val selection = ConnectionSelector(ui::chooseConnection).select(project, snapshot)) {
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
        executeSerialized(path) {
            if (connectionId == null) safeUpdate(agent, path) else createConfiguration(project, agent, path, connectionId)
        }.whenComplete { result, error ->
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
                            runSerialized(path) { safeUpdate(capability.agent, path, port = port) }
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
        if (fileSystem.isSymbolicLink(path)) {
            return McpTransactionResult.SymlinkRefused
        }
        val inspection = backendService.inspectMcpConfiguration(agent, fileSystem.read(path).toText()).join()
        if (inspection.state != McpConfigurationKind.NOT_CONFIGURED) {
            return safeUpdate(agent, path)
        }
        val connection = getGlobalSettings().serverConnections.firstOrNull { it.name == connectionId }
            ?: return McpTransactionResult.MissingConnection
        val credentials = credentialsService.getCredentials(connection)
        val token = credentials.takeIf { it.isLeft }?.left?.token?.takeIf { it.isNotBlank() } ?: ""
        if (token.isEmpty() && !ui.confirmWithoutToken(project)) {
            return McpTransactionResult.Cancelled
        }
        val generated = backendService.generateMcpConfiguration(connectionId, token).join()
        val result = safeUpdate(agent, path, generated)
        if (result == McpTransactionResult.Updated || result == McpTransactionResult.Unchanged) {
            requestRefresh()
        }
        return result
    }

    internal fun safeUpdate(agent: AiAgent, path: Path, configuration: String? = null, port: Int = idePort.get()): McpTransactionResult {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (fileSystem.isSymbolicLink(normalizedPath)) {
            return McpTransactionResult.SymlinkRefused
        }
        val snapshot = fileSystem.read(normalizedPath)
        if (configuration == null && (snapshot == null || port !in 1..65535)) {
            return McpTransactionResult.Protected
        }
        val desired = configuration ?: """{"env":{"SONARQUBE_IDE_PORT":"$port"}}"""
        val plan = backendService.planMcpConfigurationUpdate(agent, snapshot.toText(), desired).join()
        val expectedState = if (configuration == null) McpConfigurationKind.STANDALONE else McpConfigurationKind.NOT_CONFIGURED
        if (plan.state != expectedState) {
            return McpTransactionResult.Protected
        }
        val updatedBytes = plan.updatedContent?.toByteArray(StandardCharsets.UTF_8) ?: return McpTransactionResult.Protected
        if (snapshot.contentEquals(updatedBytes)) {
            return McpTransactionResult.Unchanged
        }
        val temp = fileSystem.writeSiblingTemp(normalizedPath, updatedBytes)
        try {
            if (fileSystem.isSymbolicLink(normalizedPath)) {
                return McpTransactionResult.SymlinkRefused
            }
            if (!snapshot.contentEquals(fileSystem.read(normalizedPath))) {
                return McpTransactionResult.ConcurrentEdit
            }
            if (configuration == null && idePort.get() != port) {
                return McpTransactionResult.Protected
            }
            fileSystem.replace(temp, normalizedPath)
            return McpTransactionResult.Updated
        } finally {
            runCatching { fileSystem.deleteIfExists(temp) }.onFailure { error ->
                GlobalLogOutput.get().logError("Unable to remove the temporary MCP configuration file", error)
            }
        }
    }

    internal fun executeSerialized(path: Path, action: () -> McpTransactionResult): CompletableFuture<McpTransactionResult> {
        val normalized = path.toAbsolutePath().normalize()
        return CompletableFuture.supplyAsync({ runSerialized(normalized, action) }, executor)
    }

    private fun <T> runSerialized(path: Path, action: () -> T): T =
        pathLocks.computeIfAbsent(path.toAbsolutePath().normalize()) { ReentrantLock() }.withLock(action)

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
            McpTransactionResult.SymlinkRefused ->
                "The MCP configuration is a symbolic link and was not changed. Update the linked file manually or replace the link, then retry." to NotificationType.WARNING
            null ->
                "MCP setup did not return a result. Refresh this view and retry." to NotificationType.WARNING
        }
        ui.showMessage(project, message, type)
        if (result == McpTransactionResult.MissingConnection) {
            ui.openConnectionSettings(project)
        }
    }

    private fun refreshAll() {
        refreshCallbacks.values.forEach { refresh -> runOnUiThread(ModalityState.defaultModalityState(), refresh) }
    }

    override fun dispose() {
        refreshCallbacks.clear()
    }
}

enum class McpTransactionResult {
    Updated,
    Unchanged,
    ConcurrentEdit,
    Protected,
    MissingConnection,
    Cancelled,
    SymlinkRefused
}

private fun ByteArray?.toText(): String = this?.toString(StandardCharsets.UTF_8) ?: ""
