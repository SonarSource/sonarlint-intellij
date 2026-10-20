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
package org.sonarlint.intellij.telemetry

import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.after
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiAgentIntegrationStateObservedParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationCliStateObservedParams

class SonarLintTelemetryTests : AbstractSonarLintLightTests() {
    private val backend: BackendService = mock()
    private val service: TelemetryRpcService = mock()
    private val telemetry = SonarLintTelemetry()

    @BeforeEach
    fun setUpTelemetry() {
        replaceApplicationService(BackendService::class.java, backend)
        whenever(backend.notifyAiTelemetry(any())).thenAnswer { invocation ->
            invocation.getArgument<(TelemetryRpcService) -> Unit>(0)(service)
            CompletableFuture.completedFuture<Void>(null)
        }
    }

    @Test
    fun `reports CLI uninstall through backend telemetry with IntelliJ host and no agent`() {
        telemetry.aiIntegrationAction(AiIntegrationAction.UNINSTALL_CLI, AiIntegrationActionStatus.SUCCEEDED)

        val params = argumentCaptor<AiIntegrationActionParams>()
        verify(service, timeout(1000)).aiIntegrationAction(params.capture())
        assertThat(params.firstValue.action).isEqualTo(AiIntegrationAction.UNINSTALL_CLI)
        assertThat(params.firstValue.status).isEqualTo(AiIntegrationActionStatus.SUCCEEDED)
        assertThat(params.firstValue.host).isEqualTo(AiIntegrationHost.INTELLIJ)
        assertThat(params.firstValue.agent).isNull()
    }

    @Test
    fun `reports the target agent on integration actions`() {
        telemetry.aiIntegrationAction(AiIntegrationAction.INTEGRATE_AGENT, AiIntegrationActionStatus.CANCELLED, AiAgent.CLAUDE_CODE)

        val params = argumentCaptor<AiIntegrationActionParams>()
        verify(service, timeout(1000)).aiIntegrationAction(params.capture())
        assertThat(params.firstValue.action).isEqualTo(AiIntegrationAction.INTEGRATE_AGENT)
        assertThat(params.firstValue.status).isEqualTo(AiIntegrationActionStatus.CANCELLED)
        assertThat(params.firstValue.agent).isEqualTo(AiAgent.CLAUDE_CODE)
        assertThat(params.firstValue.host).isEqualTo(AiIntegrationHost.INTELLIJ)
    }

    @Test
    fun `reports CLI installation and authentication state with IntelliJ host`() {
        telemetry.aiIntegrationCliStateObserved(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.UNAUTHENTICATED)

        val params = argumentCaptor<AiIntegrationCliStateObservedParams>()
        verify(service, timeout(1000)).aiIntegrationCliStateObserved(params.capture())
        assertThat(params.firstValue.installationStatus).isEqualTo(CliInstallationStatus.INSTALLED)
        assertThat(params.firstValue.authenticationStatus).isEqualTo(CliAuthenticationStatus.UNAUTHENTICATED)
        assertThat(params.firstValue.host).isEqualTo(AiIntegrationHost.INTELLIJ)
    }

    @Test
    fun `reports agent detection sources and global MCP state with IntelliJ host`() {
        telemetry.aiAgentIntegrationStateObserved(
            AiAgent.GITHUB_COPILOT,
            listOf(AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI),
            McpConfigurationState.CLI_MANAGED
        )

        val params = argumentCaptor<AiAgentIntegrationStateObservedParams>()
        verify(service, timeout(1000)).aiAgentIntegrationStateObserved(params.capture())
        assertThat(params.firstValue.agent).isEqualTo(AiAgent.GITHUB_COPILOT)
        assertThat(params.firstValue.detectionSources).containsExactly(AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI)
        assertThat(params.firstValue.standaloneMcpState).isEqualTo(McpConfigurationState.CLI_MANAGED)
        assertThat(params.firstValue.host).isEqualTo(AiIntegrationHost.INTELLIJ)
    }

    @Test
    fun `waits for STARTED dispatch before sending the terminal outcome`() {
        val startedDispatch = CompletableFuture<Void>()
        doAnswer { invocation ->
            invocation.getArgument<(TelemetryRpcService) -> Unit>(0)(service)
            startedDispatch
        }.doAnswer { invocation ->
            invocation.getArgument<(TelemetryRpcService) -> Unit>(0)(service)
            CompletableFuture.completedFuture<Void>(null)
        }.whenever(backend).notifyAiTelemetry(any())

        try {
            telemetry.aiIntegrationAction(AiIntegrationAction.INSTALL_CLI, AiIntegrationActionStatus.STARTED)
            verify(service, timeout(1000)).aiIntegrationAction(any())
            telemetry.aiIntegrationAction(AiIntegrationAction.INSTALL_CLI, AiIntegrationActionStatus.SUCCEEDED)

            verify(backend, after(100).times(1)).notifyAiTelemetry(any())
        } finally {
            startedDispatch.complete(null)
        }

        val params = argumentCaptor<AiIntegrationActionParams>()
        verify(service, timeout(1000).times(2)).aiIntegrationAction(params.capture())
        assertThat(params.allValues.map { it.status }).containsExactly(AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.SUCCEEDED)
    }

    @Test
    fun `dispatches off the EDT without waiting for backend completion`() {
        val dispatchedOnEdt = CompletableFuture<Boolean>()
        val dispatch = CompletableFuture<Void>()
        doAnswer {
            dispatchedOnEdt.complete(ApplicationManager.getApplication().isDispatchThread)
            dispatch
        }.whenever(backend).notifyAiTelemetry(any())

        try {
            ApplicationManager.getApplication().invokeAndWait {
                telemetry.aiIntegrationAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED)
            }

            assertThat(dispatchedOnEdt.get(1, TimeUnit.SECONDS)).isFalse()
            assertThat(dispatch).isNotDone()
        } finally {
            dispatch.complete(null)
        }
    }

    @Test
    fun `a failed asynchronous dispatch does not block subsequent reports`() {
        val failedDispatch = CompletableFuture<Void>()
        doReturn(failedDispatch).doAnswer { invocation ->
            invocation.getArgument<(TelemetryRpcService) -> Unit>(0)(service)
            CompletableFuture.completedFuture<Void>(null)
        }.whenever(backend).notifyAiTelemetry(any())

        telemetry.aiIntegrationAction(AiIntegrationAction.INSTALL_CLI, AiIntegrationActionStatus.STARTED)
        verify(backend, timeout(1000)).notifyAiTelemetry(any())
        telemetry.aiIntegrationCliStateObserved(CliInstallationStatus.NOT_INSTALLED, CliAuthenticationStatus.UNAVAILABLE)
        failedDispatch.completeExceptionally(IllegalStateException("dispatch failed"))

        val params = argumentCaptor<AiIntegrationCliStateObservedParams>()
        verify(service, timeout(1000)).aiIntegrationCliStateObserved(params.capture())
        assertThat(params.firstValue.installationStatus).isEqualTo(CliInstallationStatus.NOT_INSTALLED)
    }

    @Test
    fun `a synchronous dispatch exception is isolated and subsequent reports proceed`() {
        doThrow(IllegalStateException("dispatch failed")).doAnswer { invocation ->
            invocation.getArgument<(TelemetryRpcService) -> Unit>(0)(service)
            CompletableFuture.completedFuture<Void>(null)
        }.whenever(backend).notifyAiTelemetry(any())

        assertThatCode {
            telemetry.aiIntegrationAction(AiIntegrationAction.LOGIN_CLI, AiIntegrationActionStatus.STARTED)
            telemetry.aiAgentIntegrationStateObserved(AiAgent.JUNIE, listOf(AiAgentDetectionSource.IDE), McpConfigurationState.UNKNOWN)
        }.doesNotThrowAnyException()

        val params = argumentCaptor<AiAgentIntegrationStateObservedParams>()
        verify(service, timeout(1000)).aiAgentIntegrationStateObserved(params.capture())
        assertThat(params.firstValue.agent).isEqualTo(AiAgent.JUNIE)
        assertThat(params.firstValue.standaloneMcpState).isEqualTo(McpConfigurationState.UNKNOWN)
    }
}
