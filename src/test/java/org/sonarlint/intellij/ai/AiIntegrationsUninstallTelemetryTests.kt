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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus

class AiIntegrationsUninstallTelemetryTests : AbstractSonarLintLightTests() {
    private val backend: BackendService = mock()
    private val telemetry: SonarLintTelemetry = mock()
    private val panel: AiIntegrationsPanel = mock()
    private val uninstall = CompletableFuture<UninstallCliResponse>()
    private val statuses = mutableListOf<AiIntegrationActionStatus>()
    private val origins = mutableListOf<Project?>()
    private val installed = AiIntegrationSnapshot(
        CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null, uninstallAvailable = true),
        listOf(AgentCapability(AiAgent.CURSOR, setOf(AiAgentDetectionSource.CLI), cliIntegrationSupported = true, standaloneMcpSupported = false)),
        emptyList(), null
    )
    private lateinit var controller: AiIntegrationsController
    private lateinit var coordinator: CliOperationCoordinator
    private lateinit var intentListener: (AiIntegrationsIntent) -> Unit
    private var confirmation: () -> Boolean = { true }

    @BeforeEach
    fun setUpController() {
        doAnswer {
            intentListener = it.getArgument(0)
            null
        }.whenever(panel).setIntentListener(any())
        replaceApplicationService(SonarLintTelemetry::class.java, telemetry)
        coordinator = CliOperationCoordinator(backend, { error("terminal unused") }, ConnectionSelector(IntellijConnectionChoiceUi()), { _, _, _ -> }, ::publishAiIntegrationChange)
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(AiIntegrationListener.TOPIC, AiIntegrationListener { origins += it })
        doAnswer {
            statuses.add(it.getArgument(1))
            null
        }.whenever(telemetry).aiIntegrationAction(eq(AiIntegrationAction.UNINSTALL_CLI), any(), eq(null))
        whenever(backend.uninstallCli()).thenReturn(uninstall)
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture.completedFuture(installed))
        val registry = AiAgentRegistry { false }
        controller = AiIntegrationsController(
            project, panel, backend, registry, coordinator,
            telemetry = telemetry,
            confirmUninstall = { confirmation() },
            mcpCoordinator = McpConfigurationCoordinator(backend, registry)
        )
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
    }

    @AfterEach
    fun disposeController() {
        Disposer.dispose(controller)
    }

    @Test
    fun `disposed controller rejects uninstall without telemetry`() {
        Disposer.dispose(controller)

        clickUninstall()

        assertThat(statuses).isEmpty()
        verify(backend, never()).uninstallCli()
    }

    @Test
    fun `declining confirmation reports cancellation`() {
        confirmation = { false }

        clickUninstall()

        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.CANCELLED)
        verify(backend, never()).uninstallCli()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `a busy lease emits nothing`() {
        assertThat(coordinator.acquire(project)).isTrue()

        clickUninstall()

        assertThat(statuses).isEmpty()
        verify(backend, never()).uninstallCli()
        assertThat(coordinator.activeOperation()).isTrue()
        coordinator.cancel(project)
    }

    @Test
    fun `confirmation exception before the lease finishes the attempt`() {
        confirmation = { throw IllegalStateException("confirmation unavailable") }
        clickUninstall()
        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
        verify(backend, never()).uninstallCli()
        statuses.clear()

        confirmation = {
            Disposer.dispose(controller)
            throw IllegalStateException("confirmation unavailable")
        }
        clickUninstall()
        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.CANCELLED)
        verify(backend, never()).uninstallCli()
    }

    @Test
    fun `panel failure before the uninstall call reports failure`() {
        doThrow(IllegalStateException("panel unavailable")).whenever(panel).setCliUninstallFeedback(CliUninstallFeedback.InProgress)

        clickUninstall()

        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
        verify(backend, never()).uninstallCli()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `failed uninstall preflight finishes and invalidates once`() {
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(
            CompletableFuture.failedFuture(IllegalStateException("preflight unavailable")),
            CompletableFuture.completedFuture(installed)
        )

        clickUninstall()

        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
        assertThat(origins).containsExactly(project)
        verify(backend, never()).uninstallCli()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `unavailable uninstall preflight finishes and invalidates once`() {
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(
            CompletableFuture.completedFuture(installed.copy(cli = installed.cli.copy(uninstallAvailable = false)))
        )

        clickUninstall()

        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
        assertThat(origins).containsExactly(project)
        verify(backend, never()).uninstallCli()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `post confirmation contention fails the accepted attempt without releasing another lease`() {
        confirmation = {
            assertThat(coordinator.acquire(project)).isTrue()
            true
        }

        clickUninstall()

        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
        assertThat(origins).containsExactly(project)
        verify(backend, never()).uninstallCli()
        assertThat(coordinator.activeOperation()).isTrue()
        coordinator.cancel(project)
    }

    @Test
    fun `maps finished status and an indeterminate response`() {
        assertOutcome(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null), AiIntegrationActionStatus.SUCCEEDED)
        assertOutcome(UninstallCliResponse(UninstallCliResponse.Status.FAILED, "", "", null), AiIntegrationActionStatus.FAILED)
        assertOutcome(UninstallCliResponse(UninstallCliResponse.Status.NOT_AVAILABLE, "", "", null), AiIntegrationActionStatus.FAILED)
        assertOutcome(null, AiIntegrationActionStatus.UNKNOWN)
    }

    @Test
    fun `telemetry failure still uninstalls and releases the lease`() {
        doAnswer {
            statuses.add(it.getArgument(1))
            throw IllegalStateException("telemetry unavailable")
        }.whenever(telemetry).aiIntegrationAction(eq(AiIntegrationAction.UNINSTALL_CLI), any(), eq(null))

        clickUninstall()
        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null))
        UIUtil.dispatchAllInvocationEvents()

        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.SUCCEEDED)
        verify(backend).uninstallCli()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `asynchronous failure after disposal stays failed`() {
        clickUninstall()
        Disposer.dispose(controller)
        uninstall.completeExceptionally(IllegalStateException("backend unavailable"))
        UIUtil.dispatchAllInvocationEvents()

        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
        assertThat(coordinator.activeOperation()).isFalse()
    }

    private fun assertOutcome(response: UninstallCliResponse?, expected: AiIntegrationActionStatus) {
        statuses.clear()
        val result = CompletableFuture<UninstallCliResponse>()
        whenever(backend.uninstallCli()).thenReturn(result)
        clickUninstall()
        result.complete(response)
        UIUtil.dispatchAllInvocationEvents()
        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, expected)
    }

    private fun clickUninstall() {
        intentListener(AiIntegrationsIntent.UninstallCli)
        UIUtil.dispatchAllInvocationEvents()
    }
}
