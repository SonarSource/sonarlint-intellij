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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
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
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus

class AiIntegrationsDocumentationTelemetryTests : AbstractSonarLintLightTests() {
    private val backend: BackendService = mock()
    private val telemetry: SonarLintTelemetry = mock()
    private val panel: AiIntegrationsPanel = mock()
    private val cliCoordinator: CliOperationCoordinator = mock()
    private val mcpCoordinator: McpConfigurationCoordinator = mock()
    private val registry = AiAgentRegistry { false }
    private val controllers = mutableListOf<AiIntegrationsController>()
    private val openedLinks = mutableListOf<String>()
    private var browserFailure = false
    private lateinit var controller: AiIntegrationsController
    private lateinit var handleIntent: (AiIntegrationsIntent) -> Unit

    @BeforeEach
    fun setUpController() {
        doAnswer { handleIntent = it.getArgument(0); null }.whenever(panel).setIntentListener(any())
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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `documentation works while loading or in error without triggering observations`(loadFailed: Boolean) = ApplicationManager.getApplication().invokeAndWait {
        val discovery = CompletableFuture<AiIntegrationSnapshot>()
        if (loadFailed) discovery.completeExceptionally(IllegalStateException("discovery unavailable"))
        whenever(backend.getAiIntegrationState(project, emptyList())).thenReturn(discovery)
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        clearInvocations(backend, telemetry)

        openAllDocumentation()

        assertDocumentationActions(AiIntegrationActionStatus.UNKNOWN)
        assertThat(openedLinks).containsExactly(
            SonarLintDocumentation.Intellij.SONARQUBE_CLI_GUIDE_LINK,
            SonarLintDocumentation.Intellij.MCP_CONFIGURATION_GUIDE_LINK,
            SonarLintDocumentation.Intellij.SONAR_VORTEX_LINK,
            SonarLintDocumentation.Intellij.SONARQUBE_CLI_GUIDE_LINK
        )
        assertNoStateReports()
        verifyNoInteractions(backend, mcpCoordinator)
    }

    @Test
    fun `failed documentation handoff reports failed without reloading state`() {
        browserFailure = true

        openAllDocumentation()

        assertDocumentationActions(AiIntegrationActionStatus.FAILED)
        assertNoStateReports()
        verifyNoInteractions(backend, mcpCoordinator)
    }

    private fun createController(project: Project, panel: AiIntegrationsPanel) = AiIntegrationsController(
        project, panel, backend, registry, cliCoordinator,
        telemetry = telemetry,
        mcpCoordinator = mcpCoordinator,
        browse = { link ->
            if (browserFailure) throw IllegalStateException("browser unavailable")
            openedLinks += link
        }
    ).also(controllers::add)

    private fun assertAction(action: AiIntegrationAction, vararg expectedStatuses: AiIntegrationActionStatus) {
        val statuses = argumentCaptor<AiIntegrationActionStatus>()
        verify(telemetry, times(expectedStatuses.size)).aiIntegrationAction(eq(action), statuses.capture(), isNull())
        assertThat(statuses.allValues).containsExactly(*expectedStatuses)
    }

    private fun assertNoStateReports() {
        verify(telemetry, never()).aiIntegrationCliStateObserved(any(), any())
        verify(telemetry, never()).aiAgentIntegrationStateObserved(any(), any(), any())
    }

    private fun openAllDocumentation() {
        listOf(AiIntegrationsIntent.OpenCliDocumentation, AiIntegrationsIntent.OpenMcpDocumentation,
            AiIntegrationsIntent.OpenVortexDocumentation, AiIntegrationsIntent.TroubleshootCli).forEach(handleIntent)
    }

    private fun assertDocumentationActions(outcome: AiIntegrationActionStatus) {
        assertAction(AiIntegrationAction.OPEN_CLI_DOCUMENTATION, AiIntegrationActionStatus.STARTED, outcome, AiIntegrationActionStatus.STARTED, outcome)
        assertAction(AiIntegrationAction.OPEN_MCP_DOCUMENTATION, AiIntegrationActionStatus.STARTED, outcome)
        assertAction(AiIntegrationAction.OPEN_VORTEX_DOCUMENTATION, AiIntegrationActionStatus.STARTED, outcome)
        verify(telemetry, times(8)).aiIntegrationAction(any(), any(), isNull())
    }

    private fun capability(agent: AiAgent, vararg sources: AiAgentDetectionSource) = AgentCapability(agent, sources.toSet(), true, true)

    private fun snapshot() = AiIntegrationSnapshot(
        CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
        listOf(capability(AiAgent.CURSOR, AiAgentDetectionSource.CLI)),
        emptyList(), null
    )
}
