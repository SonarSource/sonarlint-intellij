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

import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
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
    private lateinit var controller: AiIntegrationsController
    private lateinit var coordinator: CliOperationCoordinator
    private lateinit var intentListener: (AiIntegrationsIntent) -> Unit
    private var confirmation: () -> Boolean = { true }
    private var refreshCount = 0

    @BeforeEach
    fun setUpController() {
        doAnswer {
            intentListener = it.getArgument(0)
            null
        }.whenever(panel).setIntentListener(any())
        coordinator = CliOperationCoordinator(backend, { error("terminal unused") }, CliConnectionSelector(), { _, _, _ -> }, refreshViews = {
            refreshCount++
            assertThat(statuses.last()).isNotEqualTo(AiIntegrationActionStatus.STARTED)
            controller.refresh()
        })
        doAnswer {
            assertThat(coordinator.activeOperation()).isTrue()
            statuses.add(it.getArgument(1))
            null
        }.whenever(telemetry).aiIntegrationAction(org.mockito.kotlin.eq(AiIntegrationAction.UNINSTALL_CLI), any())
        whenever(backend.uninstallCli()).thenReturn(uninstall)
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture.completedFuture(
            AiIntegrationSnapshot(
                CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null, uninstallAvailable = true),
                emptyList(), emptyList(), null
            )
        ))
        controller = AiIntegrationsController(project, panel, backend, AiAgentRegistry { false }, coordinator, telemetry) { confirmation() }
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
    }

    @AfterEach
    fun disposeController() {
        Disposer.dispose(controller)
    }

    @Test
    fun `reports success despite reset and cleanup warnings`() {
        confirmation = {
            assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED)
            true
        }

        clickUninstall()
        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "reset warning", "cleanup warning", null))

        assertLifecycle(AiIntegrationActionStatus.SUCCEEDED)
        assertThat(refreshCount).isEqualTo(1)
    }

    @Test
    fun `declining confirmation reports cancellation and does not refresh`() {
        confirmation = { false }

        clickUninstall()

        assertLifecycle(AiIntegrationActionStatus.CANCELLED)
        verify(backend, never()).uninstallCli()
        assertThat(refreshCount).isZero()
    }

    @Test
    fun `confirmation exception reports failure and releases the lease`() {
        confirmation = { throw IllegalStateException("confirmation unavailable") }

        assertThatThrownBy(::clickUninstall).hasMessage("confirmation unavailable")

        assertLifecycle(AiIntegrationActionStatus.FAILED)
        verify(backend, never()).uninstallCli()
    }

    @Test
    fun `disposal during affirmative confirmation reports cancellation and does not call the backend`() {
        confirmation = {
            Disposer.dispose(controller)
            true
        }

        clickUninstall()

        assertLifecycle(AiIntegrationActionStatus.CANCELLED)
        verify(backend, never()).uninstallCli()
        assertThat(refreshCount).isZero()
    }

    @Test
    fun `disposal during throwing confirmation reports cancellation and releases the lease`() {
        confirmation = {
            Disposer.dispose(controller)
            throw IllegalStateException("confirmation unavailable")
        }

        assertThatThrownBy(::clickUninstall).hasMessage("confirmation unavailable")

        assertLifecycle(AiIntegrationActionStatus.CANCELLED)
        verify(backend, never()).uninstallCli()
        assertThat(refreshCount).isZero()
    }

    @Test
    fun `disposed controller rejects uninstall without telemetry`() {
        Disposer.dispose(controller)

        clickUninstall()

        assertThat(statuses).isEmpty()
        verify(backend, never()).uninstallCli()
    }

    @Test
    fun `busy CLI lease rejects uninstall without telemetry`() {
        assertThat(coordinator.tryAcquire(project)).isTrue()

        clickUninstall()

        assertThat(statuses).isEmpty()
        verify(backend, never()).uninstallCli()
        coordinator.releaseWithoutSideEffects()
    }

    @Test
    fun `duplicate uninstall intent records one accepted attempt`() {
        clickUninstall()
        clickUninstall()
        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null))

        assertLifecycle(AiIntegrationActionStatus.SUCCEEDED)
        verify(backend, times(1)).uninstallCli()
    }

    @ParameterizedTest
    @EnumSource(UninstallCliResponse.Status::class)
    fun `reports the observed backend status`(status: UninstallCliResponse.Status) {
        clickUninstall()
        uninstall.complete(UninstallCliResponse(status, "", "", null))

        assertLifecycle(if (status == UninstallCliResponse.Status.UNINSTALLED) AiIntegrationActionStatus.SUCCEEDED else AiIntegrationActionStatus.FAILED)
    }

    @Test
    fun `synchronous RPC exception reports failure`() {
        whenever(backend.uninstallCli()).thenThrow(IllegalStateException("backend unavailable"))

        clickUninstall()

        assertLifecycle(AiIntegrationActionStatus.FAILED)
    }

    @Test
    fun `asynchronous RPC exception reports failure`() {
        clickUninstall()
        uninstall.completeExceptionally(IllegalStateException("backend unavailable"))

        assertLifecycle(AiIntegrationActionStatus.FAILED)
    }

    @Test
    fun `synchronous RPC exception after disposal still reports failure`() {
        whenever(backend.uninstallCli()).thenAnswer {
            Disposer.dispose(controller)
            throw IllegalStateException("backend unavailable")
        }

        clickUninstall()

        assertLifecycle(AiIntegrationActionStatus.FAILED)
    }

    @Test
    fun `asynchronous RPC exception after disposal still reports failure`() {
        clickUninstall()
        Disposer.dispose(controller)
        uninstall.completeExceptionally(IllegalStateException("backend unavailable"))

        assertLifecycle(AiIntegrationActionStatus.FAILED)
    }

    @Test
    fun `preflight exception reports failure without calling the backend`() {
        doThrow(IllegalStateException("panel unavailable")).whenever(panel).setCliUninstallFeedback(CliUninstallFeedback.InProgress)

        clickUninstall()

        assertLifecycle(AiIntegrationActionStatus.FAILED)
        verify(backend, never()).uninstallCli()
    }

    @ParameterizedTest
    @EnumSource(UninstallCliResponse.Status::class)
    fun `running uninstall preserves its observed result after disposal`(status: UninstallCliResponse.Status) {
        clickUninstall()
        Disposer.dispose(controller)
        assertThat(coordinator.activeOperation()).isTrue()
        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED)

        uninstall.complete(UninstallCliResponse(status, "", "", null))

        assertLifecycle(if (status == UninstallCliResponse.Status.UNINSTALLED) AiIntegrationActionStatus.SUCCEEDED else AiIntegrationActionStatus.FAILED)
    }

    @Test
    fun `indeterminate response reports unknown`() {
        clickUninstall()
        uninstall.complete(null)

        assertLifecycle(AiIntegrationActionStatus.UNKNOWN)
    }

    @Test
    fun `unrecognized response status reports unknown`() {
        clickUninstall()
        uninstall.complete(mock())

        assertLifecycle(AiIntegrationActionStatus.UNKNOWN)
    }

    @Test
    fun `telemetry exceptions cannot block uninstall or lease release`() {
        doAnswer {
            statuses.add(it.getArgument(1))
            throw IllegalStateException("telemetry unavailable")
        }.whenever(telemetry).aiIntegrationAction(org.mockito.kotlin.eq(AiIntegrationAction.UNINSTALL_CLI), any())

        clickUninstall()
        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null))

        assertLifecycle(AiIntegrationActionStatus.SUCCEEDED)
        verify(backend).uninstallCli()
    }

    @Test
    fun `refresh failure cannot change or duplicate the observed result`() {
        whenever(backend.getAiIntegrationState(project, emptyList())).thenThrow(IllegalStateException("refresh unavailable"))

        clickUninstall()
        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null))
        UIUtil.dispatchAllInvocationEvents()

        assertLifecycle(AiIntegrationActionStatus.SUCCEEDED)
    }

    private fun clickUninstall() = intentListener(AiIntegrationsIntent.UninstallCli)

    private fun assertLifecycle(terminal: AiIntegrationActionStatus) {
        assertThat(statuses).containsExactly(AiIntegrationActionStatus.STARTED, terminal)
        assertThat(coordinator.activeOperation()).isFalse()
    }
}
