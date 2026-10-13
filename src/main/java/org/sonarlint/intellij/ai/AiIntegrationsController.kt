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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
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
    private val mcpCoordinator: McpConfigurationCoordinator = getService(McpConfigurationCoordinator::class.java)
) : Disposable {
    private val generation = AtomicLong()
    private val started = AtomicBoolean()
    private val disposed = AtomicBoolean()
    private val confirmingUninstall = AtomicBoolean()
    private var latestSnapshot: AiIntegrationSnapshot? = null

    init {
        panel.setRefreshListener(::refresh)
        panel.setIntentListener(::handleIntent)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(AiIntegrationListener.TOPIC, AiIntegrationListener { if (started.get()) refreshAfterOperation() })
    }

    fun loadInitially() {
        if (started.compareAndSet(false, true)) {
            refresh()
        }
    }

    fun refresh() {
        if (panel.isCliUninstallBusy()) {
            return
        }
        reload(clearFeedback = true)
    }

    private fun refreshAfterOperation() {
        if (panel.isCliUninstallBusy()) {
            return
        }
        reload(clearFeedback = false)
    }

    private fun reload(clearFeedback: Boolean) {
        if (isDisposed()) {
            return
        }
        if (clearFeedback) {
            panel.setCliUninstallFeedback(null)
        }
        val requestedGeneration = generation.incrementAndGet()
        publish(requestedGeneration, AiIntegrationsPanelState.Loading)
        try {
            backendService.getAiIntegrationState(project, registry.detectedIdeAgents())
                .thenCompose(mcpCoordinator::inspectSnapshot)
                .whenComplete { snapshot, error ->
                    val state = if (error != null) {
                        AiIntegrationsPanelState.Error(userFacingMessage(error))
                    } else {
                        AiIntegrationsPanelState.Ready(snapshot)
                    }
                    publish(requestedGeneration, state)
                }
        } catch (error: Exception) {
            publish(requestedGeneration, AiIntegrationsPanelState.Error(userFacingMessage(error)))
        }
    }

    private fun publish(requestedGeneration: Long, state: AiIntegrationsPanelState) {
        runOnUiThread(project) {
            if (!isDisposed() && generation.get() == requestedGeneration) {
                latestSnapshot = when (state) {
                    is AiIntegrationsPanelState.Ready -> state.snapshot
                    else -> null
                }
                panel.render(state)
            }
        }
    }

    private fun handleIntent(intent: AiIntegrationsIntent) {
        val snapshot = latestSnapshot ?: return
        when (intent) {
            AiIntegrationsIntent.UninstallCli -> uninstallCli()
            AiIntegrationsIntent.InstallCli,
            AiIntegrationsIntent.AuthenticateCli,
            is AiIntegrationsIntent.IntegrateCli -> cliCoordinator.execute(project, snapshot, intent)
            is AiIntegrationsIntent.SetUpMcp -> mcpCoordinator.setUp(project, snapshot, intent.agent)
            is AiIntegrationsIntent.OpenMcpConfiguration -> mcpCoordinator.openConfiguration(project, intent.agent, snapshot)
        }
    }

    private fun uninstallCli() {
        if (isDisposed() || panel.isCliUninstallBusy() || !confirmingUninstall.compareAndSet(false, true)) {
            return
        }
        panel.setCliUninstallFeedback(CliUninstallFeedback.Checking)
        try {
            backendService.getAiIntegrationState(project, registry.detectedIdeAgents()).whenComplete { snapshot, error ->
                runOnUiThread(ModalityState.defaultModalityState()) {
                    when {
                        isDisposed() -> confirmingUninstall.set(false)
                        error != null -> {
                            confirmingUninstall.set(false)
                            finishUninstall(uninstallFailure(error), releaseLease = false)
                        }
                        snapshot?.cli?.uninstallAvailable != true -> {
                            confirmingUninstall.set(false)
                            finishUninstall(uninstallUnavailableFeedback(), releaseLease = false)
                        }
                        else -> confirmAndUninstallCli()
                    }
                }
            }
        } catch (error: Exception) {
            confirmingUninstall.set(false)
            finishUninstall(uninstallFailure(error), releaseLease = false)
        }
    }

    private fun confirmAndUninstallCli() {
        try {
            if (!isDisposed()) {
                panel.setCliUninstallFeedback(null)
            }
            val confirmed = try {
                confirmUninstall(project)
            } catch (error: Exception) {
                if (!isDisposed()) {
                    finishUninstall(uninstallFailure(error), releaseLease = false)
                }
                return
            }
            if (!confirmed || isDisposed()) {
                if (!isDisposed()) {
                    panel.setCliUninstallFeedback(null)
                }
                return
            }
            if (!cliCoordinator.acquire(project)) {
                return
            }
            reportUninstall(AiIntegrationActionStatus.STARTED)
            try {
                panel.setCliUninstallFeedback(CliUninstallFeedback.InProgress)
                backendService.uninstallCli().whenComplete { response, error ->
                    val feedback = when {
                        error != null -> uninstallFailure(error)
                        response == null -> uninstallUnknownFeedback()
                        else -> uninstallFeedback(response)
                    }
                    finishUninstall(feedback, releaseLease = true)
                }
            } catch (error: Exception) {
                finishUninstall(uninstallFailure(error), releaseLease = true)
            }
        } finally {
            confirmingUninstall.set(false)
        }
    }

    private fun finishUninstall(feedback: CliUninstallFeedback.Finished, releaseLease: Boolean) {
        if (releaseLease) {
            reportUninstall(when (feedback.status) {
                CliUninstallStatus.UNINSTALLED -> AiIntegrationActionStatus.SUCCEEDED
                CliUninstallStatus.FAILED, CliUninstallStatus.NOT_AVAILABLE -> AiIntegrationActionStatus.FAILED
                CliUninstallStatus.UNKNOWN -> AiIntegrationActionStatus.UNKNOWN
            })
        }
        runOnUiThread(ModalityState.defaultModalityState()) {
            try {
                notifyUninstallResult(feedback)
            } finally {
                if (!isDisposed()) {
                    panel.setCliUninstallFeedback(feedback)
                }
                if (releaseLease) {
                    cliCoordinator.release()
                } else if (!isDisposed()) {
                    reload(clearFeedback = false)
                }
            }
        }
    }

    private fun reportUninstall(status: AiIntegrationActionStatus) {
        try {
            telemetry.aiIntegrationAction(AiIntegrationAction.UNINSTALL_CLI, status)
        } catch (error: Exception) {
            Logger.getInstance(AiIntegrationsController::class.java).debug("Unable to report CLI uninstall telemetry", error)
        }
    }

    private fun isDisposed() = disposed.get() || project.isDisposed || panel.isDisposed

    private fun userFacingMessage(error: Throwable): String {
        val cause = if (error is CompletionException && error.cause != null) error.cause!! else error
        return cause.message?.takeIf { it.isNotBlank() } ?: "Unable to load AI integrations."
    }

    override fun dispose() {
        disposed.set(true)
        generation.incrementAndGet()
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
