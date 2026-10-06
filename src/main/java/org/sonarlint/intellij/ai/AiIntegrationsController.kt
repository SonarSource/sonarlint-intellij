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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.CliOperationListener
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

class AiIntegrationsController @JvmOverloads constructor(
    private val project: Project,
    private val panel: AiIntegrationsPanel,
    private val backendService: BackendService = getService(BackendService::class.java),
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val cliCoordinator: CliOperationCoordinator = getService(CliOperationCoordinator::class.java),
    private val confirmUninstall: (Project) -> Boolean = ::confirmCliUninstall
) : Disposable {
    private val generation = AtomicLong()
    private val started = AtomicBoolean()
    private val disposed = AtomicBoolean()
    private var latestSnapshot: AiIntegrationSnapshot? = null

    init {
        panel.setRefreshListener(::refresh)
        panel.setIntentListener(::handleIntent)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(CliOperationListener.TOPIC, CliOperationListener { if (started.get()) refresh() })
    }

    fun loadInitially() {
        if (started.compareAndSet(false, true)) {
            refresh()
        }
    }

    fun refresh() {
        val uninstalling = panel.isCliUninstallInProgress()
        reload(showLoading = !uninstalling, clearFeedback = !uninstalling)
    }

    private fun reload(showLoading: Boolean, clearFeedback: Boolean) {
        if (isDisposed()) {
            return
        }
        if (clearFeedback) {
            panel.setCliUninstallFeedback(null)
        }
        val requestedGeneration = generation.incrementAndGet()
        if (showLoading) {
            publish(requestedGeneration, AiIntegrationsPanelState.Loading)
        }
        try {
            backendService.getAiIntegrationState(project, registry.detectedIdeAgents()).whenComplete { snapshot, error ->
                val state = if (error != null) {
                    AiIntegrationsPanelState.Error(userFacingMessage(error))
                } else if (snapshot.agents.isEmpty()) {
                    AiIntegrationsPanelState.Empty(snapshot)
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
                    is AiIntegrationsPanelState.Empty -> state.snapshot
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
        }
    }

    private fun uninstallCli() {
        if (isDisposed() || !cliCoordinator.tryAcquire(project)) {
            return
        }
        val confirmed = try {
            confirmUninstall(project)
        } catch (error: Exception) {
            cliCoordinator.releaseWithoutSideEffects()
            throw error
        }
        if (!confirmed) {
            cliCoordinator.releaseWithoutSideEffects()
            return
        }
        panel.setCliUninstallFeedback(CliUninstallFeedback.InProgress)
        try {
            backendService.uninstallCli().whenComplete { response, error ->
                val feedback = if (error != null) uninstallFailure(error) else uninstallFeedback(response)
                cliCoordinator.releaseUninstall()
                if (!isDisposed()) {
                    runOnUiThread(project) {
                        if (!isDisposed()) {
                            showUninstallResult(feedback)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            cliCoordinator.releaseUninstall()
            if (!isDisposed()) {
                showUninstallResult(uninstallFailure(error))
            }
        }
    }

    private fun showUninstallResult(feedback: CliUninstallFeedback.Finished) {
        panel.setCliUninstallFeedback(feedback)
        reload(showLoading = false, clearFeedback = false)
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
    "This removes the shared SonarQube CLI used by terminals, IDEs, and agents.\n\n" +
        "Reset removes credentials and registered integrations and may revoke recorded server tokens."

internal fun confirmCliUninstall(project: Project): Boolean =
    MessageDialogBuilder.okCancel(CLI_UNINSTALL_TITLE, CLI_UNINSTALL_MESSAGE)
        .yesText("Uninstall")
        .noText("Cancel")
        .ask(project)
