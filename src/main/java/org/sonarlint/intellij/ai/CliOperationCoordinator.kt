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
import org.sonarlint.intellij.messages.CliOperationListener
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AuthenticateCliWithConnectionResponse.Status

@Service(Service.Level.APP)
class CliOperationCoordinator @JvmOverloads constructor(
    private val backendService: BackendService = getService(BackendService::class.java),
    private val terminalAdapterProvider: () -> CliTerminalAdapter = { CliTerminalAdapterProvider.create() },
    private val connectionSelector: CliConnectionSelector = CliConnectionSelector(),
    private val notifyUser: (Project, String, NotificationType) -> Unit = { _, message, type ->
        projectLessNotification("SonarQube CLI", message, type)
    },
    private val refreshViews: () -> Unit = {
        runOnUiThread(ModalityState.defaultModalityState()) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CliOperationListener.TOPIC).operationFinished()
        }
    },
    private val runAuthentication: (Task.Backgroundable) -> Unit = { it.queue() }
) {
    private val active = AtomicReference<OperationLease?>()
    private val lastOutcome = AtomicReference<CliOperationOutcome?>()
    fun execute(project: Project, snapshot: AiIntegrationSnapshot, intent: AiIntegrationsIntent): Boolean {
        if (project.isDisposed) {
            return false
        }
        val lease = OperationLease(project) { releaseOperation(it, CliOperationOutcome.Cancelled) }
        if (!active.compareAndSet(null, lease)) {
            active.get()?.terminalSession?.focus?.invoke()
            return false
        }
        try {
            Disposer.register(project, lease)
            when (intent) {
                AiIntegrationsIntent.InstallCli -> prepareAndLaunch(lease, backendService.prepareInstallCliCommand())
                AiIntegrationsIntent.AuthenticateCli -> when (val selection = connectionSelector.select(project, snapshot)) {
                    ConnectionSelection.Cancelled -> throw CancellationException("CLI sign-in cancelled")
                    ConnectionSelection.InteractiveLogin -> prepareAndLaunch(lease, backendService.prepareAuthenticateCliCommand(null))
                    is ConnectionSelection.Selected -> authenticateWithConnection(lease, selection.connectionId)
                }
                is AiIntegrationsIntent.IntegrateCli -> prepareAndLaunch(lease, backendService.prepareIntegrateCliCommand(intent.agent))
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
            ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState()) {
                handlePreparation(lease, command, preparationError)
            }
        }
    }

    private fun handlePreparation(lease: OperationLease, command: CliCommand?, preparationError: Throwable?) {
        if (active.get() != lease) {
            return
        }
        if (lease.project.isDisposed || lease.indicator?.isCanceled == true) {
            releaseOperation(lease, CliOperationOutcome.Cancelled)
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

    internal fun lastOutcome(): CliOperationOutcome? = lastOutcome.get()

    private fun authenticateWithConnection(lease: OperationLease, connectionId: String) {
        lease.savedTokenAuthentication = true
        runAuthentication(object : Task.Backgroundable(lease.project, "Signing in to SonarQube CLI", true) {
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
                    releaseOperation(lease, CliOperationOutcome.Cancelled)
                    return
                }
                when (response.status) {
                    Status.AUTHENTICATED -> releaseAndNotify(lease, CliOperationOutcome.Authenticated,
                        "Signed in to SonarQube CLI.", NotificationType.INFORMATION)
                    Status.INTERACTIVE_LOGIN_REQUIRED -> {
                        lease.savedTokenAuthentication = false
                        try {
                            prepareAndLaunch(lease, backendService.prepareAuthenticateCliCommand(connectionId))
                        } catch (error: Exception) {
                            onThrowable(error)
                        }
                    }
                    Status.UPGRADE_REQUIRED -> releaseAndNotify(lease, CliOperationOutcome.AuthenticationFailed,
                        "Update SonarQube CLI to the latest version to reuse a saved connection token.", NotificationType.WARNING)
                    Status.FAILED -> releaseAndNotify(lease, CliOperationOutcome.AuthenticationFailed,
                        response.message?.takeIf { it.isNotBlank() } ?: "Unable to sign in to SonarQube CLI.", NotificationType.ERROR)
                }
            }

            override fun onCancel() {
                releaseOperation(lease, CliOperationOutcome.Cancelled)
            }

            override fun onThrowable(error: Throwable) {
                if (active.get() == lease && !lease.project.isDisposed && lease.indicator?.isCanceled != true) {
                    releaseAndNotify(lease, CliOperationOutcome.AuthenticationFailed, "Unable to sign in to SonarQube CLI. Retry from this view.", NotificationType.ERROR)
                } else {
                    releaseOperation(lease, CliOperationOutcome.Cancelled)
                }
            }
        })
    }

    private fun launch(lease: OperationLease, command: CliCommand) {
        when (val launch = terminalAdapterProvider().launch(lease.project, command)) {
            is TerminalLaunch.Started -> {
                lease.terminalSession = launch
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
        if (!active.compareAndSet(lease, null)) {
            return false
        }
        lastOutcome.set(outcome)
        if ((!lease.savedTokenAuthentication || outcome == CliOperationOutcome.Authenticated) &&
            (outcome != CliOperationOutcome.Cancelled || lease.indicator == null)) refreshViews()
        return true
    }

    private fun releaseAndNotify(lease: OperationLease, outcome: CliOperationOutcome, message: String, type: NotificationType) {
        if (releaseOperation(lease, outcome)) {
            Disposer.dispose(lease)
            notifyUser(lease.project, message, type)
        }
    }
}

private class OperationLease(val project: Project, private val onDispose: (OperationLease) -> Unit) : Disposable {
    private val disposed = AtomicBoolean()
    @Volatile
    private var pending: CompletableFuture<*>? = null
    @Volatile
    var indicator: ProgressIndicator? = null
    var savedTokenAuthentication = false
    @Volatile
    var terminalSession: TerminalLaunch.Started? = null

    fun track(request: CompletableFuture<*>) {
        pending = request
        if (disposed.get()) request.cancel(true)
    }

    override fun dispose() {
        disposed.set(true)
        indicator?.cancel()
        onDispose(this)
        pending?.cancel(true)
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
