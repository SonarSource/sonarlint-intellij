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
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.UIUtil
import java.awt.Container
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JToggleButton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationInspectionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.McpConfigurationState

class AiIntegrationsControllerTests : AbstractSonarLintLightTests() {
    private lateinit var backend: BackendService
    private lateinit var panel: AiIntegrationsPanel
    private lateinit var controller: AiIntegrationsController
    private lateinit var mcpCoordinator: McpConfigurationCoordinator
    private var enabledPluginIds = emptySet<String>()

    @BeforeEach
    fun setUpController() {
        backend = mock(BackendService::class.java)
        val registry = AiAgentRegistry(IdePluginDetector { it in enabledPluginIds })
        panel = AiIntegrationsPanel(registry)
        `when`(backend.inspectMcpConfiguration(any(), any())).thenReturn(
            CompletableFuture.completedFuture(McpConfigurationInspectionResponse(McpConfigurationState.NOT_CONFIGURED, emptyList()))
        )
        mcpCoordinator = McpConfigurationCoordinator(
            backendService = backend,
            registry = registry,
            fileSystem = mock(McpFileSystem::class.java),
            executor = Runnable::run
        )
        controller = AiIntegrationsController(project, panel, backend, registry, mcpCoordinator = mcpCoordinator)
    }

    @AfterEach
    fun disposeController() {
        Disposer.dispose(controller)
    }

    @Test
    fun `loads only on first selection and stops requesting after disposal`() {
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture())

        controller.loadInitially()
        controller.loadInitially()
        verify(backend, times(1)).getAiIntegrationState(project, emptyList())

        Disposer.dispose(controller)
        controller.refresh()
        verify(backend, times(1)).getAiIntegrationState(project, emptyList())
    }

    @Test
    fun `refreshes on integration events only after first selection and until disposed`() {
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture())
        val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(AiIntegrationListener.TOPIC)

        publisher.stateChanged()
        verify(backend, never()).getAiIntegrationState(project, emptyList())

        controller.loadInitially()
        publisher.stateChanged()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())

        controller.loadInitially()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())

        Disposer.dispose(controller)
        publisher.stateChanged()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())
    }

    @Test
    fun `MCP port refresh respects first selection and controller disposal`() {
        `when`(backend.getAiIntegrationState(null, emptyList())).thenReturn(CompletableFuture.completedFuture(snapshot(emptyList())))
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture())
        fun refreshMcp() {
            mcpCoordinator.embeddedServerStarted(64121)
            UIUtil.dispatchAllInvocationEvents()
        }

        refreshMcp()
        verify(backend, never()).getAiIntegrationState(project, emptyList())

        controller.loadInitially()
        refreshMcp()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())

        Disposer.dispose(controller)
        refreshMcp()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())
    }

    @Test
    fun `ignores an older refresh result`() {
        val first = CompletableFuture<AiIntegrationSnapshot>()
        val second = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(first, second)

        controller.refresh()
        controller.refresh()
        second.complete(snapshot(listOf(AgentCapability(AiAgent.CODEX, emptySet(), true, false))))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("1 agent detected")

        first.complete(snapshot(emptyList()))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("1 agent detected")
    }

    @Test
    fun `shows a backend failure`() {
        val request = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(request)

        controller.loadInitially()
        request.completeExceptionally(IllegalStateException("Backend unavailable"))
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains("Backend unavailable")
    }

    @Test
    fun `refresh detects current plugins and updates the panel from backend capabilities`() {
        val bothJetBrainsAgents = listOf(AiAgent.GITHUB_COPILOT, AiAgent.JUNIE, AiAgent.JETBRAINS_AI_ASSISTANT)
        val onlyAiAssistant = listOf(AiAgent.JETBRAINS_AI_ASSISTANT)
        val first = CompletableFuture<AiIntegrationSnapshot>()
        val second = CompletableFuture<AiIntegrationSnapshot>()
        val third = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, bothJetBrainsAgents)).thenReturn(first)
        `when`(backend.getAiIntegrationState(project, onlyAiAssistant)).thenReturn(second)
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(third)
        enabledPluginIds = setOf("com.github.copilot", "org.jetbrains.junie", "com.intellij.ml.llm")

        controller.loadInitially()
        verify(backend).getAiIntegrationState(project, bothJetBrainsAgents)
        first.complete(snapshot(bothJetBrainsAgents.map(::hostedCapability)))
        UIUtil.dispatchAllInvocationEvents()
        descendants(panel).filterIsInstance<JToggleButton>().forEach { it.doClick() }
        assertThat(labels(panel)).contains("3 agents detected", "IDE setup: 1")
            .containsSubsequence("GitHub Copilot", "Junie", "JetBrains AI Assistant")

        enabledPluginIds = setOf("com.intellij.ml.llm")
        refreshFromPanel()
        verify(backend).getAiIntegrationState(project, onlyAiAssistant)
        second.complete(snapshot(onlyAiAssistant.map(::hostedCapability)))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("1 agent detected", "IDE setup: 0", "JetBrains AI Assistant")
            .doesNotContain("GitHub Copilot", "Junie")

        enabledPluginIds = emptySet()
        refreshFromPanel()
        verify(backend).getAiIntegrationState(project, emptyList())
        third.complete(snapshot(emptyList()))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("0 agents detected", "IDE setup: 0")
            .doesNotContain("GitHub Copilot", "Junie", "JetBrains AI Assistant")
    }

    private fun refreshFromPanel() {
        descendants(panel).filterIsInstance<JButton>().single { it.text == "Refresh" }.doClick()
    }

    private fun hostedCapability(agent: AiAgent) = AgentCapability(agent, setOf(AiAgentDetectionSource.IDE), false, true)

    private fun snapshot(agents: List<AgentCapability>) = AiIntegrationSnapshot(
        CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null),
        agents,
        emptyList(),
        null
    )

    private fun labels(container: Container): List<String> = descendants(container).filterIsInstance<JBLabel>().map { it.text }

    private fun texts(container: Container): List<String> = descendants(container).filterIsInstance<JBTextArea>().map { it.text }

    private fun descendants(container: Container): List<java.awt.Component> = container.components.flatMap { component ->
        listOf(component) + if (component is Container) descendants(component) else emptyList()
    }
}
