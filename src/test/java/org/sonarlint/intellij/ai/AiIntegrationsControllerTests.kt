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
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.CliOperationListener
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgentDetectionSource
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus

class AiIntegrationsControllerTests : AbstractSonarLintLightTests() {
    private lateinit var backend: BackendService
    private lateinit var panel: AiIntegrationsPanel
    private lateinit var controller: AiIntegrationsController
    private lateinit var coordinator: CliOperationCoordinator
    private var extraController: AiIntegrationsController? = null
    private var enabledPluginIds = emptySet<String>()
    private var acceptUninstall = true
    private var confirmationCount = 0

    @BeforeEach
    fun setUpController() {
        backend = mock(BackendService::class.java)
        replaceApplicationService(SonarLintTelemetry::class.java, mock(SonarLintTelemetry::class.java))
        coordinator = CliOperationCoordinator(backend, { error("terminal unused") }, CliConnectionSelector(), { _, _, _ -> }, {})
        val registry = AiAgentRegistry(IdePluginDetector { it in enabledPluginIds })
        panel = AiIntegrationsPanel(registry)
        controller = AiIntegrationsController(project, panel, backend, registry, coordinator) {
            confirmationCount++
            acceptUninstall
        }
    }

    @AfterEach
    fun disposeController() {
        Disposer.dispose(controller)
        extraController?.let { Disposer.dispose(it) }
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
    fun `refreshes on CLI operation events only after first selection and until disposed`() {
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture())
        val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(CliOperationListener.TOPIC)

        publisher.operationFinished()
        verify(backend, never()).getAiIntegrationState(project, emptyList())

        controller.loadInitially()
        publisher.operationFinished()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())

        controller.loadInitially()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())

        Disposer.dispose(controller)
        publisher.operationFinished()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())
    }

    @Test
    fun `ignores an older refresh result`() {
        val first = CompletableFuture<AiIntegrationSnapshot>()
        val second = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(first, second)

        controller.refresh()
        controller.refresh()
        second.complete(snapshot(listOf(AgentCapability(AiAgent.CODEX, emptySet(), false, true))))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("1 supported")

        first.complete(snapshot(emptyList()))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("1 supported")
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
        assertThat(labels(panel)).contains("3 agents detected", "3 supported")
            .containsSubsequence("GitHub Copilot", "Junie", "JetBrains AI Assistant")

        enabledPluginIds = setOf("com.intellij.ml.llm")
        refreshFromPanel()
        verify(backend).getAiIntegrationState(project, onlyAiAssistant)
        second.complete(snapshot(onlyAiAssistant.map(::hostedCapability)))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("1 agent detected", "1 supported", "JetBrains AI Assistant")
            .doesNotContain("GitHub Copilot", "Junie")

        enabledPluginIds = emptySet()
        refreshFromPanel()
        verify(backend).getAiIntegrationState(project, emptyList())
        third.complete(snapshot(emptyList()))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(labels(panel)).contains("0 agents detected", "0 supported")
            .doesNotContain("GitHub Copilot", "Junie", "JetBrains AI Assistant")
    }

    @Test
    fun `cancelled confirmation leaves the CLI installation unchanged`() {
        acceptUninstall = false
        load(installedCli())

        uninstallButton().doClick()

        verify(backend, never()).uninstallCli()
        assertThat(confirmationCount).isEqualTo(1)
        assertThat(texts(panel)).doesNotContain("Uninstalling SonarQube CLI…")
        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isNull()
        assertThat(uninstallButton().isEnabled).isTrue()
    }

    @Test
    fun `does not start uninstall while another CLI operation holds the lease`() {
        load(installedCli())
        assertThat(coordinator.tryAcquire(project)).isTrue()

        uninstallButton().doClick()

        verify(backend, never()).uninstallCli()
        assertThat(confirmationCount).isZero()
        coordinator.releaseWithoutSideEffects()
    }

    @Test
    fun `shows uninstall progress then warnings and a refreshed install action`() {
        val uninstall = CompletableFuture<UninstallCliResponse>()
        val refreshed = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.uninstallCli()).thenReturn(uninstall)
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(
            CompletableFuture.completedFuture(installedCli()),
            refreshed
        )
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()

        uninstallButton().doClick()

        assertThat(texts(panel)).contains("Uninstalling SonarQube CLI…")
        assertThat(uninstallButton().isEnabled).isFalse()
        assertThat(coordinator.activeOperation()).isTrue()
        assertThat(coordinator.execute(project, installedCli(), AiIntegrationsIntent.InstallCli)).isFalse()

        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "reset warning", "cleanup warning", null))
        UIUtil.dispatchAllInvocationEvents()
        refreshed.complete(installedCli().copy(cli = installedCli().cli.copy(
            installation = CliInstallationStatus.NOT_INSTALLED,
            uninstallAvailable = false
        )))
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains(
            "SonarQube CLI was removed, but cleanup reported warnings.",
            "reset warning",
            "cleanup warning"
        )
        assertThat(labels(panel)).contains("Reset output", "Cleanup warnings")
        assertThat(buttonTexts()).contains("Install SonarQube CLI").doesNotContain("Uninstall CLI…")
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `reports reset and deletion failures without treating them as a complete uninstall`() {
        val resetFailure = CompletableFuture<UninstallCliResponse>()
        val stillInstalled = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.uninstallCli()).thenReturn(resetFailure)
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(
            CompletableFuture.completedFuture(installedCli()),
            stillInstalled
        )
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        uninstallButton().doClick()

        resetFailure.complete(UninstallCliResponse(UninstallCliResponse.Status.FAILED, "", "reset failed", "SonarQube CLI reset failed."))
        UIUtil.dispatchAllInvocationEvents()
        stillInstalled.complete(installedCli())
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains("SonarQube CLI reset failed.", "reset failed")
        assertThat(buttonTexts()).contains("Uninstall CLI…").doesNotContain("Install SonarQube CLI")

        val deletionFailure = CompletableFuture<UninstallCliResponse>()
        val afterDeletionFailure = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.uninstallCli()).thenReturn(deletionFailure)
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(afterDeletionFailure)
        uninstallButton().doClick()
        deletionFailure.complete(UninstallCliResponse(
            UninstallCliResponse.Status.FAILED, "", "", "Could not delete the SonarQube CLI installation folder."
        ))
        UIUtil.dispatchAllInvocationEvents()
        afterDeletionFailure.complete(installedCli())
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains(
            "The SonarQube CLI executable is still installed. Could not delete the SonarQube CLI installation folder."
        )
        assertThat(buttonTexts()).contains("Uninstall CLI…")
    }

    @Test
    fun `closing the project does not free the uninstall lease until the backend finishes`() {
        val closingProject = mock(Project::class.java)
        var projectDisposed = false
        `when`(closingProject.isDisposed).thenAnswer { projectDisposed }
        val closingPanel = AiIntegrationsPanel()
        var refreshes = 0
        val localCoordinator = CliOperationCoordinator(
            backend,
            { error("terminal unused") },
            CliConnectionSelector(),
            { _, _, _ -> },
            { refreshes++ }
        )
        val local = AiIntegrationsController(closingProject, closingPanel, backend, AiAgentRegistry { false }, localCoordinator) { true }
        val loaded = CompletableFuture<AiIntegrationSnapshot>()
        val uninstall = CompletableFuture<UninstallCliResponse>()
        `when`(backend.getAiIntegrationState(closingProject, emptyList())).thenReturn(loaded)
        `when`(backend.uninstallCli()).thenReturn(uninstall)
        local.loadInitially()
        loaded.complete(installedCli())
        UIUtil.dispatchAllInvocationEvents()

        descendants(closingPanel).filterIsInstance<JButton>().single { it.text == "Uninstall CLI…" }.doClick()
        assertThat(localCoordinator.activeOperation()).isTrue()

        projectDisposed = true
        Disposer.dispose(closingProject)

        assertThat(localCoordinator.activeOperation()).isTrue()
        assertThat(localCoordinator.lastOutcome()).isNull()
        assertThat(localCoordinator.execute(project, installedCli(), AiIntegrationsIntent.InstallCli)).isFalse()
        assertThat(localCoordinator.execute(project, installedCli(), AiIntegrationsIntent.AuthenticateCli)).isFalse()
        assertThat(localCoordinator.execute(project, installedCli(), AiIntegrationsIntent.IntegrateCli(AiAgent.CURSOR))).isFalse()

        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null))
        UIUtil.dispatchAllInvocationEvents()

        assertThat(localCoordinator.activeOperation()).isFalse()
        assertThat(localCoordinator.lastOutcome()).isNull()
        assertThat(refreshes).isEqualTo(1)
        assertThat(localCoordinator.tryAcquire(project)).isTrue()
        Disposer.dispose(local)
    }

    @Test
    fun `refresh during uninstall keeps the in-progress actions disabled`() {
        val uninstall = CompletableFuture<UninstallCliResponse>()
        val duringRefresh = CompletableFuture<AiIntegrationSnapshot>()
        val installed = installedWithCliAgent()
        `when`(backend.uninstallCli()).thenReturn(uninstall)
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(
            CompletableFuture.completedFuture(installed),
            duringRefresh
        )
        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        descendants(panel).filterIsInstance<JToggleButton>().first().doClick()
        assertThat(buttonTexts()).contains("Integrate")

        uninstallButton().doClick()
        refreshFromPanel()

        assertThat(texts(panel)).contains("Uninstalling SonarQube CLI…")
        assertCliActionsStayDisabled()

        duringRefresh.complete(installed)
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains("Uninstalling SonarQube CLI…")
        assertCliActionsStayDisabled()
        assertThat(coordinator.activeOperation()).isTrue()
    }

    @Test
    fun `finishing uninstall refreshes other open project windows`() {
        Disposer.dispose(controller)
        val panelA = AiIntegrationsPanel()
        val panelB = AiIntegrationsPanel()
        val publishing = CliOperationCoordinator(backend, { error("terminal unused") }, CliConnectionSelector(), { _, _, _ -> }) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CliOperationListener.TOPIC).operationFinished()
        }
        val registry = AiAgentRegistry { false }
        controller = AiIntegrationsController(project, panelA, backend, registry, publishing) { true }
        extraController = AiIntegrationsController(project, panelB, backend, registry, publishing) { true }
        val installed = installedCli()
        val removed = installed.copy(cli = installed.cli.copy(
            installation = CliInstallationStatus.NOT_INSTALLED,
            uninstallAvailable = false
        ))
        val afterUninstall = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(
            CompletableFuture.completedFuture(installed),
            CompletableFuture.completedFuture(installed),
            afterUninstall,
            afterUninstall,
            afterUninstall
        )
        `when`(backend.uninstallCli()).thenReturn(CompletableFuture.completedFuture(
            UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "reset warning", "", null)
        ))

        controller.loadInitially()
        extraController!!.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        assertThat(descendants(panelB).filterIsInstance<JButton>().map { it.text }).contains("Uninstall CLI…")

        descendants(panelA).filterIsInstance<JButton>().single { it.text == "Uninstall CLI…" }.doClick()
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }
        afterUninstall.complete(removed)
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }

        assertThat(descendants(panelB).filterIsInstance<JButton>().map { it.text })
            .contains("Install SonarQube CLI")
            .doesNotContain("Uninstall CLI…")
        assertThat(texts(panelA)).contains("SonarQube CLI was removed, but cleanup reported warnings.", "reset warning")
    }

    @Test
    fun `a synchronous uninstall failure stays visible after the queued refresh`() {
        Disposer.dispose(controller)
        val failurePanel = AiIntegrationsPanel()
        val publishing = CliOperationCoordinator(backend, { error("terminal unused") }, CliConnectionSelector(), { _, _, _ -> }) {
            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().messageBus.syncPublisher(CliOperationListener.TOPIC).operationFinished()
            }
        }
        controller = AiIntegrationsController(project, failurePanel, backend, AiAgentRegistry { false }, publishing) { true }
        val installed = installedCli()
        val afterFailure = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(
            CompletableFuture.completedFuture(installed),
            afterFailure,
            afterFailure
        )
        `when`(backend.uninstallCli()).thenThrow(IllegalStateException("backend unavailable"))

        controller.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        descendants(failurePanel).filterIsInstance<JButton>().single { it.text == "Uninstall CLI…" }.doClick()
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }

        assertThat(texts(failurePanel)).contains("backend unavailable")

        afterFailure.complete(installed)
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }

        assertThat(texts(failurePanel)).contains("backend unavailable")
        assertThat(descendants(failurePanel).filterIsInstance<JButton>().map { it.text }).contains("Uninstall CLI…")
    }

    private fun load(snapshot: AiIntegrationSnapshot) {
        val loaded = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(loaded)
        controller.loadInitially()
        loaded.complete(snapshot)
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun installedCli() = snapshot(emptyList()).copy(
        cli = CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null, uninstallAvailable = true)
    )

    private fun installedWithCliAgent() = AiIntegrationSnapshot(
        CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null, uninstallAvailable = true),
        listOf(AgentCapability(AiAgent.CLAUDE_CODE, setOf(AiAgentDetectionSource.IDE), true, false)),
        emptyList(),
        null
    )

    private fun assertCliActionsStayDisabled() {
        val actions = descendants(panel).filterIsInstance<JButton>().filter {
            it.text in setOf("Install SonarQube CLI", "Sign in", "Integrate", "Uninstall CLI…")
        }
        assertThat(actions.map { it.text }).contains("Uninstall CLI…").doesNotContain("Integrate", "Install SonarQube CLI", "Sign in")
        assertThat(actions).allSatisfy { assertThat(it.isEnabled).isFalse() }
    }

    private fun uninstallButton() = descendants(panel).filterIsInstance<JButton>().single { it.text == "Uninstall CLI…" }

    private fun buttonTexts() = descendants(panel).filterIsInstance<JButton>().map { it.text }

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
