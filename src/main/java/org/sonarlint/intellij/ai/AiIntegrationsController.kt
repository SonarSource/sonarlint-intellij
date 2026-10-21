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

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus

class AiIntegrationsController @JvmOverloads constructor(
    private val project: Project,
    private val panel: AiIntegrationsPanel,
    private val backendService: BackendService = getService(BackendService::class.java),
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val cliCoordinator: CliOperationCoordinator = getService(CliOperationCoordinator::class.java),
    private val notifyUninstallResult: (CliUninstallFeedback.Finished) -> Unit = ::notifyCliUninstallResult,
    private val telemetry: SonarLintTelemetry = getService(SonarLintTelemetry::class.java),
    private val confirmUninstall: (Project) -> Boolean = ::confirmCliUninstall,
    private val mcpCoordinator: McpConfigurationCoordinator = getService(McpConfigurationCoordinator::class.java),
    private val browse: (String) -> Unit = { BrowserUtil.browse(it) }
) : Disposable {
    private val generation = AtomicLong()
    private val started = AtomicBoolean()
    private val disposed = AtomicBoolean()
    private val uninstallLock = Any()
    private var pendingUninstall: AiIntegrationTelemetryAttempt? = null
    private var uninstallPreflight: CompletableFuture<*>? = null
    private var latestSnapshot: AiIntegrationSnapshot? = null

    init {
        panel.setRefreshListener(::refresh)
        panel.setIntentListener(::handleIntent)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(AiIntegrationListener.TOPIC, AiIntegrationListener { origin ->
                if (started.get() && !isDisposed()) runOnUiThread(ModalityState.defaultModalityState()) {
                    if (origin === project || !panel.isCliUninstallBusy()) {
                        reload(clearFeedback = false, observe = origin === project)
                    }
                }
            })
    }

    fun loadInitially() {
        if (started.compareAndSet(false, true)) reload(clearFeedback = true, observe = true)
    }

    fun refresh() {
        if (isDisposed() || panel.isCliUninstallBusy()) return
        val attempt = AiIntegrationTelemetryAttempt(telemetry, AiIntegrationAction.REFRESH)
        reload(clearFeedback = true, observe = true, attempt)
    }

    private fun reload(clearFeedback: Boolean, observe: Boolean, attempt: AiIntegrationTelemetryAttempt? = null) {
        if (isDisposed()) {
            attempt?.finish(AiIntegrationActionStatus.CANCELLED)
            return
        }
        val requestedGeneration = generation.incrementAndGet()
        try {
            if (clearFeedback) panel.setCliUninstallFeedback(null)
            publish(requestedGeneration, AiIntegrationsPanelState.Loading)
            backendService.getAiIntegrationState(project, registry.detectedIdeAgents()).whenComplete { snapshot, error ->
                if (error != null || snapshot == null) {
                    finishLoad(requestedGeneration, null, error ?: IllegalStateException("Unable to load AI integrations."), attempt)
                } else {
                    if (observe && !isDisposed()) safelyReportAiTelemetry {
                        telemetry.aiIntegrationCliStateObserved(snapshot.cli.installation, snapshot.cli.authentication)
                    }
                    inspectSnapshot(requestedGeneration, snapshot, observe, attempt)
                }
            }
        } catch (error: Exception) {
            finishLoad(requestedGeneration, null, error, attempt)
        }
    }

    private fun inspectSnapshot(generation: Long, snapshot: AiIntegrationSnapshot, observe: Boolean, attempt: AiIntegrationTelemetryAttempt?) {
        try {
            mcpCoordinator.inspectSnapshot(snapshot).whenComplete { inspected, error ->
                if (observe) reportAgents(snapshot, inspected.takeIf { error == null })
                finishLoad(generation, inspected, error ?: if (inspected == null) IllegalStateException("Unable to inspect AI integrations.") else null, attempt)
            }
        } catch (error: Exception) {
            if (observe) reportAgents(snapshot, null)
            finishLoad(generation, null, error, attempt)
        }
    }

    private fun reportAgents(snapshot: AiIntegrationSnapshot, inspected: AiIntegrationSnapshot?) {
        snapshot.agents.groupBy { it.agent }.forEach { (agent, capabilities) ->
            if (!isDisposed()) safelyReportAiTelemetry {
                val sources = listOf(AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI)
                    .filter { source -> capabilities.any { source in it.detectionSources } }
                telemetry.aiAgentIntegrationStateObserved(agent, sources, inspected?.mcpConfigurations?.get(agent)?.state ?: McpConfigurationState.UNKNOWN)
            }
        }
    }

    private fun finishLoad(generation: Long, snapshot: AiIntegrationSnapshot?, error: Throwable?, attempt: AiIntegrationTelemetryAttempt?) {
        attempt?.finish(when {
            isAiIntegrationCancellation(error) -> AiIntegrationActionStatus.CANCELLED
            error != null -> AiIntegrationActionStatus.FAILED
            else -> AiIntegrationActionStatus.SUCCEEDED
        })
        val state = if (error != null) AiIntegrationsPanelState.Error(userFacingMessage(error)) else AiIntegrationsPanelState.Ready(snapshot!!)
        publish(generation, state)
    }

    private fun publish(requestedGeneration: Long, state: AiIntegrationsPanelState) {
        runOnUiThread(project) {
            if (!isDisposed() && generation.get() == requestedGeneration) {
                latestSnapshot = (state as? AiIntegrationsPanelState.Ready)?.snapshot
                panel.render(state)
            }
        }
    }

    private fun handleIntent(intent: AiIntegrationsIntent) {
        if (isDisposed()) return
        val documentation = when (intent) {
            AiIntegrationsIntent.OpenCliDocumentation,
            AiIntegrationsIntent.TroubleshootCli -> AiIntegrationAction.OPEN_CLI_DOCUMENTATION to SonarLintDocumentation.Intellij.SONARQUBE_CLI_GUIDE_LINK
            AiIntegrationsIntent.OpenMcpDocumentation -> AiIntegrationAction.OPEN_MCP_DOCUMENTATION to SonarLintDocumentation.Intellij.MCP_CONFIGURATION_GUIDE_LINK
            AiIntegrationsIntent.OpenVortexDocumentation -> AiIntegrationAction.OPEN_VORTEX_DOCUMENTATION to SonarLintDocumentation.Intellij.SONAR_VORTEX_LINK
            else -> null
        }
        if (documentation != null) {
            val attempt = AiIntegrationTelemetryAttempt(telemetry, documentation.first)
            val status = try {
                browse(documentation.second)
                AiIntegrationActionStatus.UNKNOWN
            } catch (_: Exception) {
                AiIntegrationActionStatus.FAILED
            }
            attempt.finish(status)
            return
        }
        val snapshot = latestSnapshot ?: return
        when (intent) {
            AiIntegrationsIntent.UninstallCli -> uninstallCli()
            AiIntegrationsIntent.InstallCli,
            AiIntegrationsIntent.AuthenticateCli,
            is AiIntegrationsIntent.IntegrateCli -> cliCoordinator.execute(project, snapshot, intent)
            is AiIntegrationsIntent.SetUpMcp -> mcpCoordinator.setUp(project, snapshot, intent.agent)
            is AiIntegrationsIntent.OpenMcpConfiguration -> mcpCoordinator.openConfiguration(project, intent.agent, snapshot)
            else -> Unit
        }
    }

    private fun uninstallCli() {
        val attempt = synchronized(uninstallLock) {
            if (isDisposed() || panel.isCliUninstallBusy() || pendingUninstall != null) return
            if (cliCoordinator.activeOperation()) {
                cliCoordinator.focusActiveOperation()
                return
            }
            AiIntegrationTelemetryAttempt(telemetry, AiIntegrationAction.UNINSTALL_CLI).also { pendingUninstall = it }
        }
        try {
            panel.setCliUninstallFeedback(CliUninstallFeedback.Checking)
            val preflight = backendService.getAiIntegrationState(project, registry.detectedIdeAgents())
            synchronized(uninstallLock) {
                if (pendingUninstall === attempt) uninstallPreflight = preflight else preflight.cancel(true)
            }
            preflight.whenComplete { snapshot, error ->
                when {
                    error != null -> finishPendingUninstall(attempt, failureStatus(error), uninstallFailure(error))
                    snapshot?.cli?.uninstallAvailable != true -> finishPendingUninstall(attempt, AiIntegrationActionStatus.FAILED, uninstallUnavailableFeedback())
                    else -> try {
                        runOnUiThread(ModalityState.defaultModalityState()) { confirmAndUninstallCli(attempt) }
                    } catch (error: Exception) {
                        finishPendingUninstall(attempt, failureStatus(error), uninstallFailure(error))
                    }
                }
            }
        } catch (error: Exception) {
            finishPendingUninstall(attempt, failureStatus(error), uninstallFailure(error))
        }
    }

    private fun confirmAndUninstallCli(attempt: AiIntegrationTelemetryAttempt) {
        if (synchronized(uninstallLock) { pendingUninstall !== attempt }) return
        try {
            if (isDisposed()) {
                finishPendingUninstall(attempt, AiIntegrationActionStatus.CANCELLED)
                return
            }
            panel.setCliUninstallFeedback(null)
            if (!confirmUninstall(project)) {
                finishPendingUninstall(attempt, AiIntegrationActionStatus.CANCELLED)
                return
            }
            if (!acquireUninstall(attempt)) {
                finishPendingUninstall(attempt, if (isDisposed()) AiIntegrationActionStatus.CANCELLED else AiIntegrationActionStatus.FAILED)
                return
            }
            performUninstall(attempt)
        } catch (error: Exception) {
            finishPendingUninstall(attempt, failureStatus(error), uninstallFailure(error))
        }
    }

    private fun acquireUninstall(attempt: AiIntegrationTelemetryAttempt): Boolean = synchronized(uninstallLock) {
        val acquired = pendingUninstall === attempt && !isDisposed() && cliCoordinator.acquireUninstall(project, attempt)
        if (acquired) {
            pendingUninstall = null
            uninstallPreflight = null
        }
        acquired
    }

    private fun performUninstall(attempt: AiIntegrationTelemetryAttempt) {
        try {
            panel.setCliUninstallFeedback(CliUninstallFeedback.InProgress)
            val request = backendService.uninstallCli()
            cliCoordinator.trackUninstall(attempt, request)
            request.whenComplete { response, error ->
                val feedback = when {
                    error != null -> uninstallFailure(error)
                    response == null -> uninstallUnknownFeedback()
                    else -> uninstallFeedback(response)
                }
                val status = if (error != null) failureStatus(error) else when (feedback.status) {
                    CliUninstallStatus.UNINSTALLED -> AiIntegrationActionStatus.SUCCEEDED
                    CliUninstallStatus.UNKNOWN -> AiIntegrationActionStatus.UNKNOWN
                    else -> AiIntegrationActionStatus.FAILED
                }
                if (cliCoordinator.completeUninstall(attempt, status)) showUninstallFeedback(feedback)
            }
        } catch (error: Exception) {
            if (cliCoordinator.completeUninstall(attempt, failureStatus(error))) showUninstallFeedback(uninstallFailure(error))
        }
    }

    private fun finishPendingUninstall(attempt: AiIntegrationTelemetryAttempt, status: AiIntegrationActionStatus, feedback: CliUninstallFeedback.Finished? = null) {
        synchronized(uninstallLock) {
            if (pendingUninstall !== attempt) return
            pendingUninstall = null
            uninstallPreflight = null
        }
        if (attempt.finish(status)) publishAiIntegrationChange(project)
        showUninstallFeedback(feedback)
    }

    private fun showUninstallFeedback(feedback: CliUninstallFeedback.Finished?) {
        runCatching {
            runOnUiThread(ModalityState.defaultModalityState()) {
                if (feedback != null) runCatching { notifyUninstallResult(feedback) }
                if (!isDisposed()) panel.setCliUninstallFeedback(feedback)
            }
        }
    }

    private fun failureStatus(error: Throwable) = if (isAiIntegrationCancellation(error)) AiIntegrationActionStatus.CANCELLED else AiIntegrationActionStatus.FAILED

    private fun isDisposed() = disposed.get() || project.isDisposed || panel.isDisposed

    private fun userFacingMessage(error: Throwable): String {
        val cause = if (error is CompletionException && error.cause != null) error.cause!! else error
        return cause.message?.takeIf { it.isNotBlank() } ?: "Unable to load AI integrations."
    }

    override fun dispose() {
        disposed.set(true)
        generation.incrementAndGet()
        synchronized(uninstallLock) {
            val preflight = uninstallPreflight
            pendingUninstall?.let { finishPendingUninstall(it, AiIntegrationActionStatus.CANCELLED) }
            preflight?.cancel(true)
        }
        cliCoordinator.cancel(project)
        panel.dispose()
    }
}

internal const val CLI_UNINSTALL_TITLE = "Uninstall SonarQube CLI"
internal const val CLI_UNINSTALL_MESSAGE =
    "This removes the shared CLI used by terminals, IDEs, and agents. " +
        "Reset removes credentials and registered integrations and may revoke recorded server tokens."

internal fun confirmCliUninstall(project: Project): Boolean =
    MessageDialogBuilder.okCancel("$CLI_UNINSTALL_TITLE?", CLI_UNINSTALL_MESSAGE)
        .icon(Messages.getWarningIcon())
        .yesText("Uninstall")
        .noText("Cancel")
        .ask(project)
