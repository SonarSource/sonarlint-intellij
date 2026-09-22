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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.BackendReadyListener

@Service(Service.Level.APP)
class McpConfigurationCoordinator @JvmOverloads constructor(
    private val backendService: BackendService = getService(BackendService::class.java),
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val ownership: McpConfigurationOwnership = getService(McpConfigurationOwnership::class.java),
    private val credentialsService: CredentialsService = getService(CredentialsService::class.java),
    private val fileSystem: McpFileSystem = NioMcpFileSystem(),
    private val ui: McpUiAdapter = IntellijMcpUiAdapter(),
    private val environment: AiIntegrationEnvironment = IntellijAiIntegrationEnvironment(),
    private val executor: Executor = AppExecutorUtil.getAppExecutorService(),
    subscribeToBackendReady: Boolean = true
) : Disposable {
    private val pathLocks = ConcurrentHashMap<Path, ReentrantLock>()
    private val refreshCallbacks = ConcurrentHashMap<Any, () -> Unit>()
    private val refreshRequested = AtomicBoolean()
    private val refreshWorkerRunning = AtomicBoolean()
    private val busConnection = if (subscribeToBackendReady) {
        ApplicationManager.getApplication().messageBus.connect()
    } else {
        null
    }

    init {
        busConnection?.subscribe(BackendReadyListener.TOPIC, BackendReadyListener(::backendReady))
    }

    fun register(owner: Any, refresh: () -> Unit) {
        refreshCallbacks[owner] = refresh
    }

    fun unregister(owner: Any) {
        refreshCallbacks.remove(owner)
    }

    fun inspectSnapshot(snapshot: AiIntegrationSnapshot): CompletableFuture<AiIntegrationSnapshot> {
        val inspections = snapshot.agents.map { capability ->
            val path = registry.standaloneMcpPath(capability.agent)
            if (path != null && capability.standaloneMcpSupported) {
                inspectForPresentation(capability.agent, path)
            } else {
                CompletableFuture.completedFuture(
                    McpAgentConfiguration(
                        capability.agent,
                        null,
                        McpConfigurationKind.CLI_ONLY,
                        false,
                        emptyList()
                    )
                )
            }
        }
        if (inspections.isEmpty()) {
            return CompletableFuture.completedFuture(snapshot)
        }
        return CompletableFuture.allOf(*inspections.toTypedArray()).thenApply {
            snapshot.copy(mcpConfigurations = inspections.associate { future ->
                val configuration = future.join()
                configuration.agent to configuration
            })
        }
    }

    fun setUp(project: Project, snapshot: AiIntegrationSnapshot, agent: AiAgentId): Boolean {
        if (environment.isRemote()) {
            return false
        }
        val configuration = snapshot.mcpConfigurations[agent]
        if (configuration == null) {
            ui.showMessage(project, "MCP configuration details are unavailable. Refresh this view and try again.", NotificationType.WARNING)
            return false
        }
        val path = configuration.path
        if (path == null || configuration.state == McpConfigurationKind.CLI_ONLY) {
            ui.showMessage(project, "This agent is configured through SonarQube CLI. Use Go to CLI to continue.", NotificationType.INFORMATION)
            return false
        }
        if (configuration.state == McpConfigurationKind.CLI_MANAGED ||
            configuration.state == McpConfigurationKind.UNKNOWN ||
            configuration.state == McpConfigurationKind.MALFORMED) {
            ui.showMessage(
                project,
                "This MCP configuration cannot be changed safely. Open the configuration, resolve the reported state, and retry.",
                NotificationType.WARNING
            )
            return false
        }
        val externalTakeover = configuration.state == McpConfigurationKind.STANDALONE && !configuration.owned
        if (externalTakeover && !ui.confirmExternalTakeover(project)) {
            ui.showMessage(project, "MCP setup was cancelled. The existing configuration was not changed.", NotificationType.INFORMATION)
            return false
        }
        val connectionId = when (val selection = McpConnectionSelector(ui).select(project, snapshot)) {
            is McpConnectionSelection.Selected -> selection.connectionId
            McpConnectionSelection.Missing -> {
                ui.showMessage(project, "No SonarQube connection is available. Add a connection, then retry MCP setup.", NotificationType.ERROR)
                ui.openConnectionSettings(project)
                return false
            }
            McpConnectionSelection.Cancelled -> {
                ui.showMessage(project, "MCP setup was cancelled. Choose Set up when you are ready to select a connection.", NotificationType.INFORMATION)
                return false
            }
        }
        executeSerialized(path) {
            safeUpdate(agent, path, connectionId, externalTakeover || configuration.owned)
        }.whenComplete { result, error ->
            ApplicationManager.getApplication().invokeLater({
                reportSetupResult(project, result, error)
                refreshAll()
            }, project.disposed)
        }
        return true
    }

    fun openConfiguration(project: Project, agent: AiAgentId, snapshot: AiIntegrationSnapshot) {
        snapshot.mcpConfigurations[agent]?.path?.let { ui.openConfiguration(project, it) }
    }

    fun openConnectionSettings(project: Project) {
        ui.openConnectionSettings(project)
    }

    fun backendReady() {
        if (environment.isRemote()) {
            return
        }
        refreshRequested.set(true)
        if (refreshWorkerRunning.compareAndSet(false, true)) {
            CompletableFuture.runAsync(::drainBackendRefreshes, executor)
        }
    }

    private fun drainBackendRefreshes() {
        try {
            while (refreshRequested.getAndSet(false)) {
                ownership.all().forEach { (agent, record) ->
                    val path = registry.standaloneMcpPath(agent)
                    if (path == null) {
                        ownership.clearIfMatches(agent, record)
                    } else {
                        runSerialized(path) { refreshOwned(agent, path, record) }
                    }
                }
            }
        } finally {
            refreshWorkerRunning.set(false)
            if (refreshRequested.get()) {
                backendReady()
            }
        }
    }

    private fun refreshOwned(agent: AiAgentId, path: Path, record: ManagedMcpOwnership): McpTransactionResult {
        if (record.fingerprint == null || !connectionExists(record.connectionId) || fileSystem.isSymbolicLink(path)) {
            ownership.clearIfMatches(agent, record)
            return McpTransactionResult.Protected
        }
        val current = fileSystem.read(path)
        if (current == null || managedFingerprint(current) != record.fingerprint) {
            ownership.clearIfMatches(agent, record)
            return McpTransactionResult.Protected
        }
        val inspection = backendService.inspectMcpConfiguration(agent, current.toText()).join()
        if (inspection.state == McpConfigurationKind.NOT_CONFIGURED || inspection.state == McpConfigurationKind.CLI_MANAGED) {
            ownership.clearIfMatches(agent, record)
            return McpTransactionResult.Protected
        }
        if (inspection.state != McpConfigurationKind.STANDALONE) {
            return McpTransactionResult.Protected
        }
        return safeUpdate(agent, path, record.connectionId, true)
    }

    private fun inspectForPresentation(agent: AiAgentId, path: Path): CompletableFuture<McpAgentConfiguration> {
        return CompletableFuture.supplyAsync({
            runSerialized(path) { inspectForPresentationSerialized(agent, path) }
        }, executor)
    }

    private fun inspectForPresentationSerialized(agent: AiAgentId, path: Path): McpAgentConfiguration {
        val bytes = fileSystem.read(path)
        val inspection = backendService.inspectMcpConfiguration(agent, bytes.toText()).join()
        val diagnostics = inspection.diagnostics.toMutableList()
        var owned = false
        ownership.record(agent)?.let { record ->
            val staleReason = when {
                record.fingerprint == null -> "Previous ownership cannot be verified. Set up this agent again."
                !connectionExists(record.connectionId) -> "The saved SonarQube connection no longer exists. Set up this agent again."
                bytes == null || managedFingerprint(bytes) != record.fingerprint ->
                    "The configuration changed since SonarQube for IDE last managed it. Set up again to take ownership."
                inspection.state == McpConfigurationKind.NOT_CONFIGURED || inspection.state == McpConfigurationKind.CLI_MANAGED ->
                    "The managed SonarQube MCP entry is no longer present. Set up this agent again."
                inspection.state == McpConfigurationKind.STANDALONE -> null
                else -> null
            }
            if (staleReason == null && inspection.state == McpConfigurationKind.STANDALONE) {
                owned = true
            } else if (staleReason != null) {
                ownership.clearIfMatches(agent, record)
                diagnostics += staleReason
            }
        }
        return McpAgentConfiguration(agent, path, inspection.state, owned, diagnostics)
    }

    internal fun safeUpdate(
        agent: AiAgentId,
        path: Path,
        connectionId: String,
        allowExistingStandalone: Boolean
    ): McpTransactionResult {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (fileSystem.isSymbolicLink(normalizedPath)) {
            return McpTransactionResult.SymlinkRefused
        }
        val snapshot = fileSystem.read(normalizedPath)
        val inspection = backendService.inspectMcpConfiguration(agent, snapshot.toText()).join()
        when (inspection.state) {
            McpConfigurationKind.CLI_MANAGED,
            McpConfigurationKind.CLI_ONLY,
            McpConfigurationKind.UNKNOWN,
            McpConfigurationKind.MALFORMED -> return McpTransactionResult.Protected
            McpConfigurationKind.STANDALONE -> if (!allowExistingStandalone) return McpTransactionResult.Protected
            McpConfigurationKind.NOT_CONFIGURED -> Unit
        }
        val connection = getGlobalSettings().serverConnections.firstOrNull { it.name == connectionId }
            ?: return McpTransactionResult.MissingConnection
        val credentials = runCatching { credentialsService.getCredentials(connection) }.getOrNull()
            ?: return McpTransactionResult.InvalidCredentials
        if (!credentials.isLeft) {
            return McpTransactionResult.InvalidCredentials
        }
        val generated = backendService.generateMcpConfiguration(connectionId, credentials).join()
        val plan = backendService.planMcpConfigurationUpdate(agent, snapshot.toText(), generated).join()
        if (plan.state == McpConfigurationKind.CLI_MANAGED ||
            plan.state == McpConfigurationKind.CLI_ONLY ||
            plan.state == McpConfigurationKind.UNKNOWN ||
            plan.state == McpConfigurationKind.MALFORMED) {
            return McpTransactionResult.Protected
        }
        val updatedBytes = plan.updatedContent?.toByteArray(StandardCharsets.UTF_8)
            ?: return McpTransactionResult.Protected
        if (sameBytes(snapshot, updatedBytes)) {
            if (fileSystem.isSymbolicLink(normalizedPath)) {
                return McpTransactionResult.SymlinkRefused
            }
            if (!sameBytes(snapshot, fileSystem.read(normalizedPath))) {
                return McpTransactionResult.ConcurrentEdit
            }
            ownership.remember(agent, connectionId, managedFingerprint(updatedBytes))
            return McpTransactionResult.Unchanged
        }
        var temp: Path? = null
        var backup: Path? = null
        var completed = false
        return try {
            backup = snapshot?.let { fileSystem.createBackup(normalizedPath, it) }
            temp = fileSystem.writeSiblingTemp(normalizedPath, updatedBytes)
            if (fileSystem.isSymbolicLink(normalizedPath)) {
                return McpTransactionResult.SymlinkRefused
            }
            if (!sameBytes(snapshot, fileSystem.read(normalizedPath))) {
                return McpTransactionResult.ConcurrentEdit
            }
            fileSystem.replace(temp, normalizedPath)
            temp = null
            ownership.remember(agent, connectionId, managedFingerprint(updatedBytes))
            completed = true
            McpTransactionResult.Updated
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            temp?.let { path ->
                runCatching { fileSystem.deleteIfExists(path) }.exceptionOrNull()?.let(cleanupFailures::add)
            }
            if (!completed) {
                backup?.let { path ->
                    runCatching { fileSystem.deleteIfExists(path) }.exceptionOrNull()?.let(cleanupFailures::add)
                }
            }
            if (cleanupFailures.isNotEmpty()) {
                cleanupFailures.drop(1).forEach(cleanupFailures.first()::addSuppressed)
                throw cleanupFailures.first()
            }
        }
    }

    internal fun executeSerialized(path: Path, action: () -> McpTransactionResult): CompletableFuture<McpTransactionResult> {
        val normalized = path.toAbsolutePath().normalize()
        return CompletableFuture.supplyAsync({ runSerialized(normalized, action) }, executor)
    }

    private fun <T> runSerialized(path: Path, action: () -> T): T =
        pathLocks.computeIfAbsent(path.toAbsolutePath().normalize()) { ReentrantLock() }.withLock(action)

    private fun connectionExists(connectionId: String): Boolean =
        getGlobalSettings().serverConnections.any { it.name == connectionId }

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
                "MCP configuration updated. Restart or reload the AI agent to use the new connection." to NotificationType.INFORMATION
            McpTransactionResult.Unchanged ->
                "MCP configuration is already up to date and is now managed by SonarQube for IDE." to NotificationType.INFORMATION
            McpTransactionResult.ConcurrentEdit ->
                "The MCP configuration changed during setup. Review the file and retry so no edits are lost." to NotificationType.WARNING
            McpTransactionResult.Protected ->
                "The MCP configuration was not changed because its current state cannot be updated safely. Open the file, resolve the issue, and retry." to NotificationType.WARNING
            McpTransactionResult.MissingConnection ->
                "The selected SonarQube connection no longer exists. Add or select a connection, then retry." to NotificationType.ERROR
            McpTransactionResult.InvalidCredentials ->
                "The selected connection needs valid token credentials. Update its credentials, then retry MCP setup." to NotificationType.ERROR
            McpTransactionResult.SymlinkRefused ->
                "The MCP configuration is a symbolic link and was not changed. Update the linked file manually or replace the link, then retry." to NotificationType.WARNING
            null ->
                "MCP setup did not return a result. Refresh this view and retry." to NotificationType.WARNING
        }
        ui.showMessage(project, message, type)
        if (result == McpTransactionResult.MissingConnection || result == McpTransactionResult.InvalidCredentials) {
            ui.openConnectionSettings(project)
        }
    }

    private fun refreshAll() {
        refreshCallbacks.values.forEach { refresh ->
            ApplicationManager.getApplication().invokeLater(refresh)
        }
    }

    override fun dispose() {
        busConnection?.disconnect()
        refreshCallbacks.clear()
    }
}

enum class McpTransactionResult {
    Updated,
    Unchanged,
    ConcurrentEdit,
    Protected,
    MissingConnection,
    InvalidCredentials,
    SymlinkRefused
}

private fun ByteArray?.toText(): String = this?.toString(StandardCharsets.UTF_8) ?: ""

private fun sameBytes(first: ByteArray?, second: ByteArray?): Boolean = when {
    first == null && second == null -> true
    first == null || second == null -> false
    else -> first.contentEquals(second)
}

internal fun managedFingerprint(content: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(content)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
