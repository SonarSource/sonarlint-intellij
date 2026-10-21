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
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus

class AiIntegrationsTelemetryTests : AbstractSonarLintLightTests() {
    private val backend: BackendService = mock()
    private val telemetry: SonarLintTelemetry = mock()
    private val panel: AiIntegrationsPanel = mock()
    private val cliCoordinator: CliOperationCoordinator = mock()
    private val mcpCoordinator: McpConfigurationCoordinator = mock()
    private val registry = AiAgentRegistry { false }
    private val controllers = mutableListOf<AiIntegrationsController>()
    private lateinit var controller: AiIntegrationsController
    private lateinit var refreshFromPanel: () -> Unit

    @BeforeEach
    fun setUpController() {
        doAnswer { refreshFromPanel = it.getArgument(0); null }.whenever(panel).setRefreshListener(any())
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture.completedFuture(snapshot()))
        whenever(mcpCoordinator.inspectSnapshot(any())).thenAnswer { invocation ->
            CompletableFuture.completedFuture(invocation.getArgument<AiIntegrationSnapshot>(0))
        }
        controller = createController(project, panel)
    }

    @AfterEach
    fun disposeControllers() {
        controllers.filterNot(Disposer::isDisposed).forEach(Disposer::dispose)
        UIUtil.dispatchAllInvocationEvents()
    }

    @Test
    fun `reports CLI state before waiting for global MCP inspection`() {
        val inspection = CompletableFuture<AiIntegrationSnapshot>()
        doReturn(inspection).whenever(mcpCoordinator).inspectSnapshot(any())

        controller.loadInitially()

        verify(telemetry).aiIntegrationCliStateObserved(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED)
        verify(telemetry, never()).aiAgentIntegrationStateObserved(any(), any(), any())
        verify(telemetry, never()).aiIntegrationAction(any(), any(), anyOrNull())

        inspection.complete(snapshot().copy(mcpConfigurations = mapOf(AiAgent.CURSOR to configuration(AiAgent.CURSOR, McpConfigurationState.STANDALONE))))

        verify(telemetry).aiAgentIntegrationStateObserved(AiAgent.CURSOR, listOf(AiAgentDetectionSource.CLI), McpConfigurationState.STANDALONE)
        verify(telemetry, times(1)).aiIntegrationCliStateObserved(any(), any())
    }

    @Test
    fun `groups agents and ordered detection sources using inspected global state`() {
        val detected = snapshot().copy(
            agents = listOf(
                capability(AiAgent.CURSOR, AiAgentDetectionSource.CLI),
                capability(AiAgent.JUNIE, AiAgentDetectionSource.IDE),
                capability(AiAgent.CURSOR, AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI),
                capability(AiAgent.GITHUB_COPILOT, AiAgentDetectionSource.IDE)
            ),
            mcpConfigurations = mapOf(AiAgent.CURSOR to configuration(AiAgent.CURSOR, McpConfigurationState.NOT_CONFIGURED))
        )
        val inspected = detected.copy(mcpConfigurations = mapOf(
            AiAgent.CURSOR to configuration(AiAgent.CURSOR, McpConfigurationState.CLI_MANAGED),
            AiAgent.GITHUB_COPILOT to configuration(AiAgent.GITHUB_COPILOT, McpConfigurationState.NOT_CONFIGURED),
            AiAgent.CODEX to configuration(AiAgent.CODEX, McpConfigurationState.STANDALONE)
        ))
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture.completedFuture(detected))
        doReturn(CompletableFuture.completedFuture(inspected)).whenever(mcpCoordinator).inspectSnapshot(detected)

        controller.loadInitially()

        verify(telemetry).aiAgentIntegrationStateObserved(AiAgent.CURSOR, listOf(AiAgentDetectionSource.IDE, AiAgentDetectionSource.CLI), McpConfigurationState.CLI_MANAGED)
        verify(telemetry).aiAgentIntegrationStateObserved(AiAgent.GITHUB_COPILOT, listOf(AiAgentDetectionSource.IDE), McpConfigurationState.NOT_CONFIGURED)
        verify(telemetry).aiAgentIntegrationStateObserved(AiAgent.JUNIE, listOf(AiAgentDetectionSource.IDE), McpConfigurationState.UNKNOWN)
        verify(telemetry, times(3)).aiAgentIntegrationStateObserved(any(), any(), any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `failed global inspection preserves CLI observation and reports unknown agent state`(synchronousFailure: Boolean) {
        val failure = IllegalStateException("inspection unavailable")
        if (synchronousFailure) {
            doThrow(failure).whenever(mcpCoordinator).inspectSnapshot(any())
        } else {
            doReturn(CompletableFuture.failedFuture<AiIntegrationSnapshot>(failure)).whenever(mcpCoordinator).inspectSnapshot(any())
        }

        controller.refresh()

        verify(telemetry).aiIntegrationCliStateObserved(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED)
        verify(telemetry).aiAgentIntegrationStateObserved(AiAgent.CURSOR, listOf(AiAgentDetectionSource.CLI), McpConfigurationState.UNKNOWN)
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
    }

    @Test
    fun `first selection reports once while reselection and background changes stay silent`() {
        publishChange(null)
        verifyNoInteractions(telemetry)
        verify(backend, never()).getAiIntegrationState(project, emptyList())

        controller.loadInitially()
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()

        verify(backend).getAiIntegrationState(project, emptyList())
        verify(telemetry).aiIntegrationCliStateObserved(any(), any())
        verify(telemetry).aiAgentIntegrationStateObserved(any(), any(), any())
        verify(telemetry, never()).aiIntegrationAction(any(), any(), anyOrNull())
        clearInvocations(telemetry, backend)

        publishChange(null)
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()

        verify(backend).getAiIntegrationState(project, emptyList())
        verifyNoInteractions(telemetry)
    }

    @Test
    fun `each explicit panel refresh reports a new observation even when state is unchanged`() {
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        clearInvocations(telemetry)

        refreshFromPanel()
        refreshFromPanel()

        verify(telemetry, times(2)).aiIntegrationCliStateObserved(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED)
        verify(telemetry, times(2)).aiAgentIntegrationStateObserved(AiAgent.CURSOR, listOf(AiAgentDetectionSource.CLI), McpConfigurationState.UNKNOWN)
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.SUCCEEDED,
            AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.SUCCEEDED)
    }

    @Test
    fun `refresh succeeds only after both discovery and inspection complete`() {
        val discovery = CompletableFuture<AiIntegrationSnapshot>()
        val inspection = CompletableFuture<AiIntegrationSnapshot>()
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(discovery)
        doReturn(inspection).whenever(mcpCoordinator).inspectSnapshot(any())

        refreshFromPanel()
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED)
        assertNoStateReports()

        discovery.complete(snapshot())
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED)
        verify(telemetry).aiIntegrationCliStateObserved(any(), any())
        verify(telemetry, never()).aiAgentIntegrationStateObserved(any(), any(), any())

        inspection.complete(snapshot())
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.SUCCEEDED)
        verify(telemetry).aiAgentIntegrationStateObserved(any(), any(), any())
    }

    @Test
    fun `failed discovery ends refresh without inventing state observations`() {
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture.failedFuture(IllegalStateException("discovery unavailable")))

        refreshFromPanel()

        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.FAILED)
        assertNoStateReports()
        verifyNoInteractions(mcpCoordinator)
    }

    @Test
    fun `cancelled discovery ends refresh as cancelled without observations`() {
        val discovery = CompletableFuture<AiIntegrationSnapshot>()
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(discovery)

        refreshFromPanel()
        discovery.cancel(false)

        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.CANCELLED)
        assertNoStateReports()
        verifyNoInteractions(mcpCoordinator)
    }

    @Test
    fun `setup completion refreshes both projects but only its origin reports observations`() {
        val otherProject: Project = mock()
        val otherPanel: AiIntegrationsPanel = mock()
        val otherController = createController(otherProject, otherPanel)
        val otherSnapshot = snapshot().copy(cli = snapshot().cli.copy(
            installation = CliInstallationStatus.NOT_INSTALLED,
            authentication = CliAuthenticationStatus.UNAVAILABLE
        ))
        whenever(backend.getAiIntegrationState(otherProject, emptyList())).thenReturn(CompletableFuture.completedFuture(otherSnapshot))
        controller.loadInitially()
        otherController.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        clearInvocations(backend, telemetry)
        whenever(panel.isCliUninstallBusy()).thenReturn(true)

        publishChange(project)

        verify(backend).getAiIntegrationState(project, emptyList())
        verify(backend).getAiIntegrationState(otherProject, emptyList())
        verify(telemetry).aiIntegrationCliStateObserved(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED)
        verify(telemetry, times(1)).aiIntegrationCliStateObserved(any(), any())
        verify(telemetry, times(1)).aiAgentIntegrationStateObserved(any(), any(), any())
        verify(telemetry, never()).aiIntegrationAction(any(), any(), anyOrNull())
    }

    @Test
    fun `superseded refreshes retain observations while only the latest result is rendered`() {
        val firstDiscovery = CompletableFuture<AiIntegrationSnapshot>()
        val secondDiscovery = CompletableFuture<AiIntegrationSnapshot>()
        val firstSnapshot = snapshot().copy(cli = snapshot().cli.copy(authentication = CliAuthenticationStatus.UNAUTHENTICATED))
        val secondSnapshot = snapshot()
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(firstDiscovery, secondDiscovery)

        controller.refresh()
        controller.refresh()
        secondDiscovery.complete(secondSnapshot)
        UIUtil.dispatchAllInvocationEvents()
        firstDiscovery.complete(firstSnapshot)
        UIUtil.dispatchAllInvocationEvents()

        val authentication = argumentCaptor<CliAuthenticationStatus>()
        verify(telemetry, times(2)).aiIntegrationCliStateObserved(eq(CliInstallationStatus.INSTALLED), authentication.capture())
        assertThat(authentication.allValues).containsExactly(CliAuthenticationStatus.AUTHENTICATED, CliAuthenticationStatus.UNAUTHENTICATED)
        verify(telemetry, times(2)).aiAgentIntegrationStateObserved(any(), any(), any())
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.STARTED,
            AiIntegrationActionStatus.SUCCEEDED, AiIntegrationActionStatus.SUCCEEDED)
        val states = argumentCaptor<AiIntegrationsPanelState>()
        verify(panel, atLeastOnce()).render(states.capture())
        assertThat(states.allValues.filterIsInstance<AiIntegrationsPanelState.Ready>().map { it.snapshot }).containsExactly(secondSnapshot)
    }

    @Test
    fun `disposal suppresses state observations without changing a known refresh outcome`() {
        val discovery = CompletableFuture<AiIntegrationSnapshot>()
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(discovery)
        controller.refresh()

        Disposer.dispose(controller)
        discovery.complete(snapshot())
        UIUtil.dispatchAllInvocationEvents()

        assertNoStateReports()
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.SUCCEEDED)
        verify(panel, never()).render(any())
    }

    @Test
    fun `disposal during inspection suppresses remaining agent observations`() {
        val inspection = CompletableFuture<AiIntegrationSnapshot>()
        doReturn(inspection).whenever(mcpCoordinator).inspectSnapshot(any())
        controller.loadInitially()
        verify(telemetry).aiIntegrationCliStateObserved(any(), any())
        clearInvocations(telemetry)

        Disposer.dispose(controller)
        inspection.complete(snapshot())

        verifyNoInteractions(telemetry)
    }

    @Test
    fun `telemetry failures do not prevent state inspection rendering or refresh completion`() {
        doThrow(IllegalStateException("telemetry unavailable")).whenever(telemetry).aiIntegrationCliStateObserved(any(), any())
        doThrow(IllegalStateException("telemetry unavailable")).whenever(telemetry).aiAgentIntegrationStateObserved(any(), any(), any())

        controller.refresh()
        UIUtil.dispatchAllInvocationEvents()

        verify(mcpCoordinator).inspectSnapshot(snapshot())
        verify(panel).render(AiIntegrationsPanelState.Ready(snapshot()))
        assertAction(AiIntegrationAction.REFRESH, AiIntegrationActionStatus.STARTED, AiIntegrationActionStatus.SUCCEEDED)
    }

    private fun createController(project: Project, panel: AiIntegrationsPanel) = AiIntegrationsController(
        project, panel, backend, registry, cliCoordinator,
        telemetry = telemetry,
        mcpCoordinator = mcpCoordinator
    ).also(controllers::add)

    private fun publishChange(origin: Project?) {
        ApplicationManager.getApplication().messageBus.syncPublisher(AiIntegrationListener.TOPIC).stateChanged(origin)
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun assertAction(action: AiIntegrationAction, vararg expectedStatuses: AiIntegrationActionStatus) {
        val statuses = argumentCaptor<AiIntegrationActionStatus>()
        verify(telemetry, times(expectedStatuses.size)).aiIntegrationAction(eq(action), statuses.capture(), isNull())
        assertThat(statuses.allValues).containsExactly(*expectedStatuses)
    }

    private fun assertNoStateReports() {
        verify(telemetry, never()).aiIntegrationCliStateObserved(any(), any())
        verify(telemetry, never()).aiAgentIntegrationStateObserved(any(), any(), any())
    }

    private fun capability(agent: AiAgent, vararg sources: AiAgentDetectionSource) = AgentCapability(agent, sources.toSet(), true, true)

    private fun configuration(agent: AiAgent, state: McpConfigurationState) = McpAgentConfiguration(agent, Path.of("/tmp/global-mcp.json"), state, emptyList())

    private fun snapshot() = AiIntegrationSnapshot(
        CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
        listOf(capability(AiAgent.CURSOR, AiAgentDetectionSource.CLI)),
        emptyList(), null
    )
}
