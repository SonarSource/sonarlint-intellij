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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus

class CliOperationCoordinatorTests : AbstractSonarLintLightTests() {
    private val command = CliCommand("sonar", listOf("auth", ""), true)
    private val snapshot = AiIntegrationSnapshot(
        CliState(CliInstallationStatus.NOT_INSTALLED, CliAuthenticationStatus.UNKNOWN, null, null, null),
        emptyList(),
        emptyList(),
        null
    )

    @Test
    fun `leases one application operation and focuses its terminal on repeat`() {
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val completion = CompletableFuture<TerminalCompletion>()
        val terminal = mock<CliTerminalAdapter>()
        val focuses = AtomicInteger()
        whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Started({ focuses.incrementAndGet() }, completion))
        val coordinator = coordinator(backend, terminal)

        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isTrue()
        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isFalse()
        assertThat(focuses.get()).isEqualTo(1)
        completion.complete(TerminalCompletion.Exited(0))
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.ExitZero)
    }

    @Test
    fun `focuses the active terminal without opening another connection chooser`() {
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val terminal = mock<CliTerminalAdapter>()
        val focuses = AtomicInteger()
        whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Started({ focuses.incrementAndGet() }, CompletableFuture()))
        var choices = 0
        val selector = CliConnectionSelector { _, _ -> choices++; null }
        val coordinator = coordinator(backend, terminal, selector = selector)
        val connections = snapshot.copy(connectionChoices = listOf(
            IntegrationConnection("first", "https://first", null),
            IntegrationConnection("second", "https://second", null)
        ))

        coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)
        assertThat(coordinator.execute(project, connections, AiIntegrationsIntent.AuthenticateCli)).isFalse()

        assertThat(choices).isZero()
        assertThat(focuses.get()).isEqualTo(1)
        assertThat(coordinator.activeOperation()).isTrue()
    }

    @Test
    fun `releases the lease when command preparation throws synchronously`() {
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenThrow(IllegalStateException("startup failed"))
            .thenReturn(CompletableFuture.completedFuture(command))
        val coordinator = coordinator(backend, SafeOptionalTerminalAdapter())

        coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)

        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.PreparationFailed)
        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isTrue()
    }

    @Test
    fun `project disposal releases the lease and ignores late preparation`() {
        val closingProject = mock<Project>()
        val preparation = CompletableFuture<CliCommand>()
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(preparation)
        val terminal = mock<CliTerminalAdapter>()
        val notifications = mutableListOf<Notification>()
        val refreshes = AtomicInteger()
        val coordinator = coordinator(backend, terminal, notifications = notifications, refresh = { refreshes.incrementAndGet() })

        coordinator.execute(closingProject, snapshot, AiIntegrationsIntent.InstallCli)
        Disposer.dispose(closingProject)
        preparation.complete(command)

        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.Cancelled)
        assertThat(refreshes.get()).isEqualTo(1)
        assertThat(notifications).isEmpty()
        verifyNoInteractions(terminal)
    }

    @Test
    fun `ignores terminal completion after its project has been disposed`() {
        val closingProject = mock<Project>()
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val completion = CompletableFuture<TerminalCompletion>()
        val terminal = mock<CliTerminalAdapter>()
        whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Started({}, completion))
        val notifications = mutableListOf<Notification>()
        val coordinator = coordinator(backend, terminal, notifications = notifications)

        coordinator.execute(closingProject, snapshot, AiIntegrationsIntent.InstallCli)
        Disposer.dispose(closingProject)
        completion.complete(TerminalCompletion.Exited(0))

        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.Cancelled)
        assertThat(notifications).isEmpty()
    }

    @Test
    fun `successful completion releases the operation once before project disposal`() {
        val closingProject = mock<Project>()
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val terminal = mock<CliTerminalAdapter>()
        whenever(terminal.launch(any(), any())).thenReturn(
            TerminalLaunch.Started({}, CompletableFuture.completedFuture(TerminalCompletion.Exited(0)))
        )
        val notifications = mutableListOf<Notification>()
        val refreshes = AtomicInteger()
        val coordinator = coordinator(backend, terminal, notifications = notifications, refresh = { refreshes.incrementAndGet() })

        coordinator.execute(closingProject, snapshot, AiIntegrationsIntent.InstallCli)
        Disposer.dispose(closingProject)

        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.ExitZero)
        assertThat(refreshes.get()).isEqualTo(1)
        assertThat(notifications).hasSize(1)
    }

    @Test
    fun `maps terminal completion outcomes without treating unknown close as cancellation`() {
        val cases = listOf(
            TerminalCompletion.Cancelled to CliOperationOutcome.Cancelled,
            TerminalCompletion.Exited(0) to CliOperationOutcome.ExitZero,
            TerminalCompletion.Exited(17) to CliOperationOutcome.NonZero,
            TerminalCompletion.ClosedWithoutExitStatus to CliOperationOutcome.Unknown
        )

        cases.forEach { (completionValue, expected) ->
            val backend = mock<BackendService>()
            whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
            val completion = CompletableFuture<TerminalCompletion>()
            val terminal = mock<CliTerminalAdapter>()
            whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Started({}, completion))
            val notifications = mutableListOf<Notification>()
            val coordinator = coordinator(backend, terminal, notifications = notifications)
            coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)

            completion.complete(completionValue)

            assertThat(coordinator.lastOutcome()).isEqualTo(expected)
            val notification = notifications.single()
            assertThat(notification.message).isNotBlank()
            assertThat(notification.message).matches("(?is).*(refresh|retry|review).*")
        }
    }

    @Test
    fun `reports explicit connection cancellation without preparing a command`() {
        val backend = mock<BackendService>()
        val notifications = mutableListOf<Notification>()
        val cancelledSelector = CliConnectionSelector(ConnectionChoiceUi { _, _ -> null })
        val multipleConnections = snapshot.copy(
            connectionChoices = listOf(
                IntegrationConnection("first", "https://first", null),
                IntegrationConnection("second", "https://second", null)
            )
        )
        val coordinator = coordinator(
            backend,
            SafeOptionalTerminalAdapter(),
            selector = cancelledSelector,
            notifications = notifications
        )

        assertThat(coordinator.execute(project, multipleConnections, AiIntegrationsIntent.AuthenticateCli)).isFalse()

        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.Cancelled)
        assertThat(notifications.single().message).contains("cancelled").contains("try again")
        verifyNoInteractions(backend)
    }

    @Test
    fun `distinguishes preparation and launch failures and refreshes active controllers`() {
        val refreshes = AtomicInteger()
        val preparationNotifications = mutableListOf<Notification>()
        val failedBackend = mock<BackendService>()
        whenever(failedBackend.prepareInstallCliCommand()).thenReturn(CompletableFuture.failedFuture(IllegalStateException("failed")))
        val preparationCoordinator = coordinator(
            failedBackend,
            SafeOptionalTerminalAdapter(),
            notifications = preparationNotifications,
            refresh = { refreshes.incrementAndGet() }
        )
        preparationCoordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)
        assertThat(preparationCoordinator.lastOutcome()).isEqualTo(CliOperationOutcome.PreparationFailed)
        assertThat(preparationNotifications.single().message).contains("Check").contains("retry")

        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val terminal = mock<CliTerminalAdapter>()
        whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Failed(IllegalStateException("failed")))
        val launchNotifications = mutableListOf<Notification>()
        val launchCoordinator = coordinator(backend, terminal, notifications = launchNotifications, refresh = { refreshes.incrementAndGet() })
        launchCoordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)
        assertThat(launchCoordinator.lastOutcome()).isEqualTo(CliOperationOutcome.LaunchFailed)
        assertThat(launchNotifications.single().message).contains("terminal").contains("retry")
        assertThat(refreshes.get()).isEqualTo(2)
    }

    @Test
    fun `reports the required Terminal plugin and releases the operation`() {
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val notifications = mutableListOf<Notification>()
        val coordinator = coordinator(
            backend,
            SafeOptionalTerminalAdapter(),
            notifications = notifications
        )

        coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)

        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.LaunchFailed)
        assertThat(notifications.single().message).contains("Terminal plugin").contains("Enable").contains("retry")
        assertThat(notifications.single().type).isEqualTo(NotificationType.ERROR)
    }

    @Test
    fun `releases the lease when the terminal adapter throws`() {
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val terminal = mock<CliTerminalAdapter>()
        whenever(terminal.launch(any(), any())).thenThrow(IllegalStateException("project closed"))
        val notifications = mutableListOf<Notification>()
        val coordinator = coordinator(backend, terminal, notifications = notifications)

        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isTrue()

        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.LaunchFailed)
        assertThat(notifications.single().message).contains("could not be started").contains("Retry")
        whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Unsupported)
        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isTrue()
    }

    @Test
    fun background_preparation_launches_on_the_edt() {
        val preparation = CompletableFuture<CliCommand>()
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(preparation)
        val terminal = mock<CliTerminalAdapter>()
        val completion = CompletableFuture<TerminalCompletion>()
        whenever(terminal.launch(any(), any())).thenAnswer {
            assertThat(ApplicationManager.getApplication().isDispatchThread).isTrue()
            TerminalLaunch.Started({}, completion)
        }
        val coordinator = coordinator(backend, terminal)
        coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)

        CompletableFuture.runAsync { preparation.complete(command) }.get(10, TimeUnit.SECONDS)
        verifyNoInteractions(terminal)
        UIUtil.dispatchAllInvocationEvents()

        verify(terminal).launch(project, command)
        assertThat(coordinator.activeOperation()).isTrue()
        completion.complete(TerminalCompletion.Exited(0))
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.ExitZero)
    }

    @Test
    fun project_disposal_cancels_a_launch_queued_on_the_edt() {
        val closingProject = mock<Project>()
        val preparation = CompletableFuture<CliCommand>()
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(preparation)
        val terminal = mock<CliTerminalAdapter>()
        val coordinator = coordinator(backend, terminal)
        coordinator.execute(closingProject, snapshot, AiIntegrationsIntent.InstallCli)

        CompletableFuture.runAsync { preparation.complete(command) }.get(10, TimeUnit.SECONDS)
        Disposer.dispose(closingProject)
        UIUtil.dispatchAllInvocationEvents()

        verifyNoInteractions(terminal)
        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.Cancelled)
    }

    @Test
    fun resolves_the_current_terminal_adapter_for_each_operation() {
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val first = SafeOptionalTerminalAdapter()
        val replacement = mock<CliTerminalAdapter>()
        whenever(replacement.launch(any(), any())).thenReturn(
            TerminalLaunch.Started({}, CompletableFuture.completedFuture(TerminalCompletion.Exited(0)))
        )
        var selected: CliTerminalAdapter = first
        val coordinator = coordinator(backend, first, provider = { selected })

        coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.LaunchFailed)
        selected = replacement
        coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)

        verify(replacement).launch(project, command)
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.ExitZero)
    }

    private fun coordinator(
        backend: BackendService,
        terminal: CliTerminalAdapter,
        selector: CliConnectionSelector = CliConnectionSelector(),
        notifications: MutableList<Notification> = mutableListOf(),
        refresh: () -> Unit = {},
        provider: () -> CliTerminalAdapter = { terminal }
    ) = CliOperationCoordinator(
        backend,
        provider,
        selector,
        { _, message, type -> notifications += Notification(message, type) },
        refresh
    )

    private data class Notification(val message: String, val type: NotificationType)
}
