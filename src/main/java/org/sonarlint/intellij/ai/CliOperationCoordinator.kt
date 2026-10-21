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
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ModalityUiUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.projectLessNotification
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse.Status

@Service(Service.Level.APP)
class CliOperationCoordinator @JvmOverloads constructor(
    private val backendService: BackendService = getService(BackendService::class.java),
    private val terminalAdapterProvider: () -> CliTerminalAdapter = { CliTerminalAdapterProvider.create() },
    private val connectionSelector: ConnectionSelector = ConnectionSelector(IntellijConnectionChoiceUi()),
    private val notifyUser: (Project, String, NotificationType) -> Unit = { _, message, type ->
        projectLessNotification("SonarQube CLI", message, type)
    },
    private val refreshViews: (Project?) -> Unit = ::publishAiIntegrationChange,
    private val runAuthentication: (Task.Backgroundable) -> Unit = { it.queue() },
    private val telemetry: SonarLintTelemetry = getService(SonarLintTelemetry::class.java)
) {
    private val active = AtomicReference<OperationLease?>()
    private val lastOutcome = AtomicReference<CliOperationOutcome?>()
    fun execute(project: Project, snapshot: AiIntegrationSnapshot, intent: AiIntegrationsIntent): Boolean {
        val action = when (intent) {
            AiIntegrationsIntent.InstallCli -> AiIntegrationAction.INSTALL_CLI
            AiIntegrationsIntent.AuthenticateCli -> AiIntegrationAction.LOGIN_CLI
            is AiIntegrationsIntent.IntegrateCli -> AiIntegrationAction.INTEGRATE_AGENT
            else -> return false
        }
        if (!acquire(project)) {
            return false
        }
        val lease = active.get() ?: return false
        lease.attempt = AiIntegrationTelemetryAttempt(telemetry, action, (intent as? AiIntegrationsIntent.IntegrateCli)?.agent)
        try {
            Disposer.register(project, lease)
            if (active.get() != lease) return false
            when (intent) {
                AiIntegrationsIntent.InstallCli -> prepareAndLaunch(lease, backendService.prepareInstallCliCommand())
                AiIntegrationsIntent.AuthenticateCli -> when (val selection = connectionSelector.select(project, snapshot.connectionChoices, snapshot.recommendedConnectionId)) {
                    ConnectionSelection.Cancelled -> throw CancellationException("CLI sign-in cancelled")
                    ConnectionSelection.Missing -> prepareAndLaunch(lease, backendService.prepareAuthenticateCliCommand(null))
                    is ConnectionSelection.Selected -> authenticateWithConnection(lease, selection.connectionId)
                }
                is AiIntegrationsIntent.IntegrateCli -> prepareAndLaunch(lease, backendService.prepareIntegrateCliCommand(intent.agent))
                else -> throw IllegalArgumentException("Not a CLI operation: $intent")
            }
        } catch (_: CancellationException) {
            releaseAndNotify(lease, CliOperationOutcome.Cancelled,
                "SonarQube CLI login was cancelled. No command was run; choose Log in to try again.", NotificationType.INFORMATION)
            return false
        } catch (error: Exception) {
            prepareAndLaunch(lease, CompletableFuture.failedFuture(error))
        }
        return true
    }

    private fun prepareAndLaunch(lease: OperationLease, preparation: CompletableFuture<CliCommand>) {
        lease.track(preparation)
        preparation.whenComplete { command, preparationError ->
            try {
                ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState()) {
                    handlePreparation(lease, command, preparationError)
                }
            } catch (_: Exception) {
                releaseOperation(lease, CliOperationOutcome.PreparationFailed)
            }
        }
    }

    private fun handlePreparation(lease: OperationLease, command: CliCommand?, preparationError: Throwable?) {
        if (active.get() != lease) {
            return
        }
        if (lease.project.isDisposed || lease.indicator?.isCanceled == true || isAiIntegrationCancellation(preparationError)) {
            releaseOperation(lease, CliOperationOutcome.Cancelled)
            Disposer.dispose(lease)
        } else if (preparationError != null || command == null) {
            releaseAndNotify(lease, CliOperationOutcome.PreparationFailed,
                "Unable to prepare the SonarQube CLI command. Check the selected connection and retry.", NotificationType.ERROR)
        } else {
            try {
                launch(lease, command)
            } catch (_: Exception) {
                releaseAndNotify(lease, CliOperationOutcome.LaunchFailed,
                    "The SonarQube CLI command could not be started. Retry from this view.", NotificationType.ERROR)
            }
        }
    }

    internal fun activeOperation(): Boolean = active.get() != null

    internal fun focusActiveOperation() {
        active.get()?.terminalSession?.focus?.invoke()
    }

    internal fun lastOutcome(): CliOperationOutcome? = lastOutcome.get()

    private fun authenticateWithConnection(lease: OperationLease, connectionId: String) {
        runAuthentication(object : Task.Backgroundable(lease.project, "Authenticating SonarQube CLI", true) {
            private lateinit var response: AuthenticateCliWithConnectionResponse

            override fun run(indicator: ProgressIndicator) {
                lease.indicator = indicator
                indicator.checkCanceled()
                if (active.get() != lease || lease.project.isDisposed) throw ProcessCanceledException()
                val request = backendService.authenticateCliWithConnection(connectionId)
                lease.track(request)
                try {
                    response = ProgressIndicatorUtils.awaitWithCheckCanceled(request, indicator)
                    indicator.checkCanceled()
                } finally {
                    if (indicator.isCanceled) request.cancel(true)
                }
            }

            override fun onSuccess() {
                if (active.get() != lease || lease.project.isDisposed || lease.indicator?.isCanceled == true) {
                    onCancel()
                    return
                }
                when (response.status) {
                    Status.AUTHENTICATED -> releaseAndNotify(lease, CliOperationOutcome.Authenticated,
                        "SonarQube CLI authenticated.", NotificationType.INFORMATION)
                    Status.INTERACTIVE_LOGIN_REQUIRED -> prepareInteractiveAuthentication(lease, connectionId)
                    Status.UPGRADE_REQUIRED -> releaseAndNotify(lease, CliOperationOutcome.AuthenticationFailed,
                        "Update SonarQube CLI to the latest version to reuse a saved connection token.", NotificationType.WARNING)
                    Status.FAILED -> releaseAndNotify(lease, CliOperationOutcome.AuthenticationFailed,
                        response.message?.takeIf { it.isNotBlank() } ?: "Unable to authenticate SonarQube CLI.", NotificationType.ERROR)
                }
            }

            override fun onCancel() {
                releaseOperation(lease, CliOperationOutcome.Cancelled)
                Disposer.dispose(lease)
            }

            override fun onThrowable(error: Throwable) {
                if (active.get() == lease && !lease.project.isDisposed && lease.indicator?.isCanceled != true) {
                    releaseAndNotify(lease, CliOperationOutcome.AuthenticationFailed, "Unable to authenticate SonarQube CLI. Retry from this view.", NotificationType.ERROR)
                } else {
                    onCancel()
                }
            }
        })
    }

    private fun prepareInteractiveAuthentication(lease: OperationLease, connectionId: String) {
        val preparation: CompletableFuture<CliCommand> = try {
            backendService.prepareAuthenticateCliCommand(connectionId)
        } catch (error: Exception) {
            CompletableFuture.failedFuture(error)
        }
        prepareAndLaunch(lease, preparation)
    }

    internal fun acquire(project: Project): Boolean = acquire(project, null, cancelWithProject = true)

    // Cancelling an RPC future does not establish that backend filesystem work has stopped.
    internal fun acquireUninstall(project: Project, attempt: AiIntegrationTelemetryAttempt): Boolean =
        acquire(project, attempt, cancelWithProject = false)

    private fun acquire(project: Project, attempt: AiIntegrationTelemetryAttempt?, cancelWithProject: Boolean): Boolean {
        if (project.isDisposed) {
            return false
        }
        val lease = OperationLease(project, attempt, cancelWithProject) { releaseOperation(it, CliOperationOutcome.Cancelled) }
        if (!active.compareAndSet(null, lease)) {
            focusActiveOperation()
            return false
        }
        return true
    }

    internal fun release() {
        active.get()?.let { releaseLease(it, AiIntegrationActionStatus.UNKNOWN) }
    }

    internal fun completeUninstall(attempt: AiIntegrationTelemetryAttempt, status: AiIntegrationActionStatus): Boolean {
        val lease = active.get()?.takeIf { it.attempt === attempt } ?: return false
        return releaseLease(lease, status)
    }

    internal fun trackUninstall(attempt: AiIntegrationTelemetryAttempt, request: CompletableFuture<*>) {
        active.get()?.takeIf { it.attempt === attempt }?.track(request) ?: request.cancel(true)
    }

    internal fun cancel(project: Project) {
        active.get()?.takeIf { it.project === project && it.cancelWithProject }?.let { releaseOperation(it, CliOperationOutcome.Cancelled) }
    }

    private fun launch(lease: OperationLease, command: CliCommand) {
        when (val launch = terminalAdapterProvider().launch(lease.project, command)) {
            is TerminalLaunch.Started -> {
                lease.terminalSession = launch
                lease.track(launch.completion)
                launch.completion.whenComplete { completion, error ->
                    handleTerminalCompletion(lease, completion, error)
                }
            }
            is TerminalLaunch.Failed -> releaseAndNotify(lease, CliOperationOutcome.LaunchFailed,
                "The SonarQube CLI terminal could not be started. Check the terminal output and retry.", NotificationType.ERROR)
            TerminalLaunch.Unsupported -> releaseAndNotify(lease, CliOperationOutcome.LaunchFailed,
                "SonarQube CLI setup requires the Terminal plugin. Enable it in Settings > Plugins and retry.", NotificationType.ERROR)
        }
    }

    private fun handleTerminalCompletion(
        lease: OperationLease,
        completion: TerminalCompletion?,
        error: Throwable?
    ) {
        val (outcome, message, type) = when {
            isAiIntegrationCancellation(error) -> Triple(
                CliOperationOutcome.Cancelled,
                "The CLI command was cancelled. Retry when ready.",
                NotificationType.INFORMATION
            )
            error != null -> Triple(
                CliOperationOutcome.Unknown,
                "The CLI command was launched, but its result could not be determined. Check the terminal output and refresh this view.",
                NotificationType.WARNING
            )
            completion is TerminalCompletion.Exited && completion.exitCode == 0 -> Triple(
                CliOperationOutcome.ExitZero,
                "The CLI command exited successfully. Refreshing the detected CLI and integration state.",
                NotificationType.INFORMATION
            )
            completion is TerminalCompletion.Exited -> Triple(
                CliOperationOutcome.NonZero,
                "The CLI command exited with code ${completion.exitCode}. Review the terminal output and retry.",
                NotificationType.ERROR
            )
            completion === TerminalCompletion.Cancelled -> Triple(
                CliOperationOutcome.Cancelled,
                "The CLI command was cancelled. No integration change was confirmed; retry when ready.",
                NotificationType.WARNING
            )
            else -> Triple(
                CliOperationOutcome.Unknown,
                "The CLI command was launched, but its exit status is unavailable. Check the terminal output and refresh this view.",
                NotificationType.WARNING
            )
        }
        releaseAndNotify(lease, outcome, message, type)
    }

    private fun releaseOperation(lease: OperationLease, outcome: CliOperationOutcome): Boolean {
        val status = when (outcome) {
            CliOperationOutcome.Authenticated, CliOperationOutcome.ExitZero -> AiIntegrationActionStatus.SUCCEEDED
            CliOperationOutcome.Cancelled -> AiIntegrationActionStatus.CANCELLED
            CliOperationOutcome.Unknown -> AiIntegrationActionStatus.UNKNOWN
            else -> AiIntegrationActionStatus.FAILED
        }
        return releaseLease(lease, status, outcome)
    }

    private fun releaseLease(lease: OperationLease, status: AiIntegrationActionStatus, outcome: CliOperationOutcome? = null): Boolean {
        if (!active.compareAndSet(lease, null)) return false
        if (outcome != null) lastOutcome.set(outcome)
        // Cleanup and telemetry never depend on project-bound UI callbacks or optional feedback.
        try {
            Disposer.dispose(lease)
        } finally {
            lease.attempt?.finish(status)
            try {
                refreshViews(lease.project)
            } catch (_: Exception) {
                com.intellij.openapi.diagnostic.Logger.getInstance(CliOperationCoordinator::class.java).debug("Unable to refresh AI integrations")
            }
        }
        return true
    }

    private fun releaseAndNotify(lease: OperationLease, outcome: CliOperationOutcome, message: String, type: NotificationType) {
        if (releaseOperation(lease, outcome) && !lease.project.isDisposed) {
            runCatching { notifyUser(lease.project, message, type) }
        }
    }
}

private class OperationLease(
    val project: Project,
    var attempt: AiIntegrationTelemetryAttempt?,
    val cancelWithProject: Boolean,
    private val onDispose: (OperationLease) -> Unit
) : Disposable {
    private val disposed = AtomicBoolean()
    @Volatile
    private var pending: CompletableFuture<*>? = null
    @Volatile
    var indicator: ProgressIndicator? = null
    @Volatile
    var terminalSession: TerminalLaunch.Started? = null

    fun track(request: CompletableFuture<*>) {
        pending = request
        if (disposed.get()) request.cancel(true)
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        try {
            indicator?.cancel()
        } finally {
            try {
                onDispose(this)
            } finally {
                pending?.cancel(true)
            }
        }
    }
}

enum class CliOperationOutcome {
    PreparationFailed,
    Authenticated,
    AuthenticationFailed,
    LaunchFailed,
    Cancelled,
    ExitZero,
    NonZero,
    Unknown
}
