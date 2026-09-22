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
import java.util.concurrent.CompletableFuture
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

class CliOperationCoordinatorTests : AbstractSonarLintLightTests() {
    private val command = CliCommand("sonar", listOf("auth", ""), true)
    private val snapshot = AiIntegrationSnapshot(
        CliState(CliInstallationState.NOT_INSTALLED, CliAuthenticationState.UNKNOWN, null, null, null),
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
        whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Started("handle", completion))
        val coordinator = coordinator(backend, terminal)

        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isTrue()
        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isFalse()
        verify(terminal).focus("handle")
        completion.complete(TerminalCompletion.Exited(0))
        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.ExitZero)
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
            whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Started(Any(), completion))
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
            notifications = preparationNotifications
        )
        preparationCoordinator.register(this, refreshes::incrementAndGet)
        preparationCoordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)
        assertThat(preparationCoordinator.lastOutcome()).isEqualTo(CliOperationOutcome.PreparationFailed)
        assertThat(preparationNotifications.single().message).contains("Check").contains("retry")

        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        val terminal = mock<CliTerminalAdapter>()
        whenever(terminal.launch(any(), any())).thenReturn(TerminalLaunch.Failed(IllegalStateException("failed")))
        val launchNotifications = mutableListOf<Notification>()
        val launchCoordinator = coordinator(backend, terminal, notifications = launchNotifications)
        launchCoordinator.register(this, refreshes::incrementAndGet)
        launchCoordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)
        assertThat(launchCoordinator.lastOutcome()).isEqualTo(CliOperationOutcome.LaunchFailed)
        assertThat(launchNotifications.single().message).contains("terminal").contains("copied").contains("refresh")
        assertThat(refreshes.get()).isEqualTo(2)
    }

    @Test
    fun `uses safe copy fallback when terminal API is unavailable`() {
        val backend = mock<BackendService>()
        whenever(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture.completedFuture(command))
        var copied: String? = null
        val notifications = mutableListOf<Notification>()
        val coordinator = coordinator(
            backend,
            SafeOptionalTerminalAdapter(),
            copier = { copied = it },
            notifications = notifications
        )

        coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)

        assertThat(coordinator.lastOutcome()).isEqualTo(CliOperationOutcome.Copied)
        assertThat(copied).contains("'sonar'").contains("''")
        assertThat(notifications.single().message).contains("Paste").contains("refresh")
    }

    @Test
    fun `selects the terminal plugin adapter and preserves rendered argument boundaries`() {
        assertThat(CliTerminalAdapterProvider.create()).isInstanceOf(IntellijTerminalAdapter::class.java)
        var launchedCommand: String? = null
        val session = object : TerminalSession {
            override fun commandShell(project: com.intellij.openapi.project.Project): CommandShell = CommandShell.POSIX

            override fun launch(project: com.intellij.openapi.project.Project, renderedCommand: String): TerminalLaunch {
                launchedCommand = renderedCommand
                return TerminalLaunch.Started(Any(), CompletableFuture.completedFuture(TerminalCompletion.ClosedWithoutExitStatus))
            }

            override fun focus(handle: Any): Boolean = true
        }

        IntellijTerminalAdapter(session).launch(project, command)

        assertThat(launchedCommand).contains("'sonar'").contains("''")
    }

    @Test
    fun `remote IDE never prepares or executes a local command`() {
        val backend = mock<BackendService>()
        val coordinator = coordinator(backend, SafeOptionalTerminalAdapter(), remote = true)

        assertThat(coordinator.execute(project, snapshot, AiIntegrationsIntent.InstallCli)).isFalse()
        verifyNoInteractions(backend)
    }

    private fun coordinator(
        backend: BackendService,
        terminal: CliTerminalAdapter,
        copier: (String) -> Unit = {},
        remote: Boolean = false,
        selector: CliConnectionSelector = CliConnectionSelector(),
        notifications: MutableList<Notification> = mutableListOf()
    ) = CliOperationCoordinator(
        backend,
        terminal,
        selector,
        AiIntegrationEnvironment { remote },
        copier,
        { _, message, type -> notifications += Notification(message, type) },
        { refresh -> refresh() }
    )

    private data class Notification(val message: String, val type: NotificationType)
}
