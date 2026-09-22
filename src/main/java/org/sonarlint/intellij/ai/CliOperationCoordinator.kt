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
import com.intellij.openapi.components.Service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.awt.datatransfer.StringSelection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.projectLessNotification

@Service(Service.Level.APP)
class CliOperationCoordinator @JvmOverloads constructor(
    private val backendService: BackendService = getService(BackendService::class.java),
    private val terminalAdapter: CliTerminalAdapter = CliTerminalAdapterProvider.create(),
    private val connectionSelector: CliConnectionSelector = CliConnectionSelector(),
    private val environment: AiIntegrationEnvironment = IntellijAiIntegrationEnvironment(),
    private val copyCommand: (String) -> Unit = { CopyPasteManager.getInstance().setContents(StringSelection(it)) },
    private val notifyUser: (Project, String, NotificationType) -> Unit = { project, message, type ->
        projectLessNotification("SonarQube CLI", message, type)
    },
    private val dispatchRefresh: (() -> Unit) -> Unit = { refresh ->
        ApplicationManager.getApplication().invokeLater(refresh)
    }
) {
    private val active = AtomicReference<OperationLease?>()
    private val lastOutcome = AtomicReference<CliOperationOutcome?>()
    private val refreshCallbacks = ConcurrentHashMap<Any, () -> Unit>()

    fun register(owner: Any, refresh: () -> Unit) {
        refreshCallbacks[owner] = refresh
    }

    fun unregister(owner: Any) {
        refreshCallbacks.remove(owner)
    }

    fun execute(project: Project, snapshot: AiIntegrationSnapshot, intent: AiIntegrationsIntent): Boolean {
        if (environment.isRemote()) {
            return false
        }
        val action = when (val resolution = toAction(project, snapshot, intent)) {
            is ActionResolution.Ready -> resolution.action
            ActionResolution.Cancelled -> {
                publishOutcome(CliOperationOutcome.Cancelled)
                notify(project, "SonarQube CLI sign-in was cancelled. No command was run; choose Sign in to try again.", NotificationType.INFORMATION)
                return false
            }
            ActionResolution.NotApplicable -> return false
        }
        val lease = OperationLease(project, action)
        if (!active.compareAndSet(null, lease)) {
            active.get()?.terminalHandle?.let(terminalAdapter::focus)
            return false
        }
        prepare(action).whenComplete { command, preparationError ->
            if (preparationError != null) {
                complete(lease, CliOperationOutcome.PreparationFailed)
                notify(project, "Unable to prepare the SonarQube CLI command. Check the selected connection and retry.", NotificationType.ERROR)
            } else {
                launchOrCopy(lease, command)
            }
        }
        return true
    }

    internal fun activeOperation(): Boolean = active.get() != null

    internal fun lastOutcome(): CliOperationOutcome? = lastOutcome.get()

    private fun toAction(
        project: Project,
        snapshot: AiIntegrationSnapshot,
        intent: AiIntegrationsIntent
    ): ActionResolution = when (intent) {
        AiIntegrationsIntent.InstallCli -> ActionResolution.Ready(CliOperationAction.Install)
        AiIntegrationsIntent.AuthenticateCli -> when (val selection = connectionSelector.select(project, snapshot)) {
            ConnectionSelection.Cancelled -> ActionResolution.Cancelled
            ConnectionSelection.InteractiveLogin -> ActionResolution.Ready(CliOperationAction.Authenticate(null))
            is ConnectionSelection.Selected -> ActionResolution.Ready(CliOperationAction.Authenticate(selection.connectionId))
        }
        is AiIntegrationsIntent.IntegrateCli -> ActionResolution.Ready(CliOperationAction.Integrate(intent.agent))
        else -> ActionResolution.NotApplicable
    }

    private fun prepare(action: CliOperationAction): CompletableFuture<CliCommand> = when (action) {
        CliOperationAction.Install -> backendService.prepareInstallCliCommand()
        is CliOperationAction.Authenticate -> backendService.prepareAuthenticateCliCommand(action.connectionId)
        is CliOperationAction.Integrate -> backendService.prepareIntegrateCliCommand(action.agent)
    }

    private fun launchOrCopy(lease: OperationLease, command: CliCommand) {
        when (val launch = terminalAdapter.launch(lease.project, command)) {
            is TerminalLaunch.Started -> {
                lease.terminalHandle = launch.handle
                launch.completion.whenComplete { completion, error ->
                    completeTerminalOperation(lease, completion, error)
                }
            }
            is TerminalLaunch.Failed -> {
                val copied = copyFallback(command)
                complete(lease, CliOperationOutcome.LaunchFailed)
                val message = if (copied) {
                    "The terminal could not be started. The command was copied; paste it into a terminal, run it, then refresh this view."
                } else {
                    "The terminal could not be started and the command could not be copied. Open a terminal and retry from this view."
                }
                notify(lease.project, message, NotificationType.WARNING)
            }
            TerminalLaunch.Unsupported -> {
                if (copyFallback(command)) {
                    complete(lease, CliOperationOutcome.Copied)
                    notify(lease.project, "The SonarQube CLI command was copied. Paste it into a terminal, run it, then refresh this view.", NotificationType.INFORMATION)
                } else {
                    complete(lease, CliOperationOutcome.LaunchFailed)
                    notify(lease.project, "No compatible terminal is available and the command could not be copied. Open a terminal and retry from this view.", NotificationType.ERROR)
                }
            }
        }
    }

    private fun copyFallback(command: CliCommand): Boolean = try {
        val shell = if (SystemInfo.isWindows) CommandShell.POWERSHELL else CommandShell.POSIX
        copyCommand(CliCommandRenderer.render(command, shell))
        true
    } catch (_: RuntimeException) {
        false
    }

    private fun completeTerminalOperation(
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
        complete(lease, outcome)
        notify(lease.project, message, type)
    }

    private fun complete(lease: OperationLease, outcome: CliOperationOutcome) {
        lastOutcome.set(outcome)
        lease.outcome.complete(outcome)
        if (active.compareAndSet(lease, null)) {
            refreshCallbacks.values.forEach { refresh ->
                dispatchRefresh(refresh)
            }
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        notifyUser(project, message, type)
    }

    private fun publishOutcome(outcome: CliOperationOutcome) {
        lastOutcome.set(outcome)
        refreshCallbacks.values.forEach { refresh -> dispatchRefresh(refresh) }
    }
}

internal data class OperationLease(
    val project: Project,
    val action: CliOperationAction,
    val outcome: CompletableFuture<CliOperationOutcome> = CompletableFuture(),
    var terminalHandle: Any? = null
)

sealed interface CliOperationAction {
    data object Install : CliOperationAction
    data class Authenticate(val connectionId: String?) : CliOperationAction
    data class Integrate(val agent: AiAgentId) : CliOperationAction
}

private sealed interface ActionResolution {
    data class Ready(val action: CliOperationAction) : ActionResolution
    data object Cancelled : ActionResolution
    data object NotApplicable : ActionResolution
}

enum class CliOperationOutcome {
    PreparationFailed,
    LaunchFailed,
    Cancelled,
    ExitZero,
    NonZero,
    Unknown,
    Copied
}
