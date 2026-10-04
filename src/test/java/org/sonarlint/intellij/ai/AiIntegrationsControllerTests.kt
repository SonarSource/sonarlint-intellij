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
import com.intellij.openapi.actionSystem.impl.ActionButton
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
    private val registry = AiAgentRegistry(IdePluginDetector { it in enabledPluginIds })
    private var acceptUninstall = true
    private var confirmationCount = 0
    private var duringConfirmation: () -> Unit = {}
    private val results = mutableListOf<CliUninstallFeedback.Finished>()

    @BeforeEach
    fun setUpController() {
        backend = mock(BackendService::class.java)
        coordinator = createCoordinator()
        replaceApplicationService(SonarLintTelemetry::class.java, mock(SonarLintTelemetry::class.java))
        panel = AiIntegrationsPanel(registry)
        controller = createController()
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
    fun `rechecks uninstall availability before prompting`() {
        load(installedCli())
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture.completedFuture(removedCli()))

        uninstallButton().click()
        UIUtil.dispatchAllInvocationEvents()

        verify(backend, never()).uninstallCli()
        assertThat(confirmationCount).isZero()
        assertThat(results.single().summary).isEqualTo(CLI_UNINSTALL_UNAVAILABLE)
        assertThat(buttonTexts()).contains("Install SonarQube CLI").doesNotContain("Uninstall CLI…")
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `checking disables refresh and confirmation leaves the shared lock free`() {
        load(installedWithCliAgent())
        descendants(panel).filterIsInstance<JToggleButton>().first().doClick()
        val checking = CompletableFuture<AiIntegrationSnapshot>()
        val uninstall = CompletableFuture<UninstallCliResponse>()
        val installed = installedWithCliAgent()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(checking)
        `when`(backend.uninstallCli()).thenReturn(uninstall)
        `when`(backend.prepareInstallCliCommand()).thenReturn(CompletableFuture())
        `when`(backend.prepareAuthenticateCliCommand(null)).thenReturn(CompletableFuture())
        `when`(backend.prepareIntegrateCliCommand(AiAgent.CURSOR)).thenReturn(CompletableFuture())
        duringConfirmation = {
            assertThat(texts(panel)).doesNotContain(
                "Checking SonarQube CLI uninstall availability…",
                "Uninstalling SonarQube CLI…"
            )
            assertThat(refreshButton().isEnabled).isTrue()
            assertThat(coordinator.activeOperation()).isFalse()
            assertThat(coordinator.execute(project, installed, AiIntegrationsIntent.InstallCli)).isTrue()
            coordinator.release()
            assertThat(coordinator.execute(project, installed, AiIntegrationsIntent.AuthenticateCli)).isTrue()
            coordinator.release()
            assertThat(coordinator.execute(project, installed, AiIntegrationsIntent.IntegrateCli(AiAgent.CURSOR))).isTrue()
            coordinator.release()
            assertThat(coordinator.activeOperation()).isFalse()
            verify(backend, never()).uninstallCli()
        }

        uninstallButton().click()
        controller.refresh()
        refreshFromPanel()

        assertThat(texts(panel)).contains("Checking SonarQube CLI uninstall availability…")
            .doesNotContain("Uninstalling SonarQube CLI…")
        assertThat(refreshButton().isEnabled).isFalse()
        assertCliActionsStayDisabled()
        assertThat(coordinator.activeOperation()).isFalse()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())
        assertThat(confirmationCount).isZero()
        verify(backend, never()).uninstallCli()

        checking.complete(installed)
        UIUtil.dispatchAllInvocationEvents()

        assertThat(confirmationCount).isEqualTo(1)
        verify(backend).uninstallCli()
        assertThat(texts(panel)).contains("Uninstalling SonarQube CLI…")
            .doesNotContain("Checking SonarQube CLI uninstall availability…")
        assertThat(refreshButton().isEnabled).isFalse()
        assertCliActionsStayDisabled()
        assertThat(coordinator.activeOperation()).isTrue()
    }

    @Test
    fun `disposal while checking availability does not prompt or hold the lock`() {
        load(installedCli())
        val checking = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(checking)

        uninstallButton().click()
        uninstallButton().click()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())
        assertThat(coordinator.activeOperation()).isFalse()
        Disposer.dispose(controller)
        checking.complete(installedCli())
        UIUtil.dispatchAllInvocationEvents()

        verify(backend, never()).uninstallCli()
        assertThat(confirmationCount).isZero()
        assertThat(results).isEmpty()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `disposal during confirmation does not start uninstall`() {
        load(installedCli())
        duringConfirmation = { Disposer.dispose(controller) }

        uninstallButton().click()
        UIUtil.dispatchAllInvocationEvents()

        verify(backend, never()).uninstallCli()
        assertThat(confirmationCount).isEqualTo(1)
        assertThat(results).isEmpty()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `cancelled confirmation leaves the CLI installation unchanged`() {
        acceptUninstall = false
        load(installedCli())

        uninstallButton().click()
        UIUtil.dispatchAllInvocationEvents()

        verify(backend, never()).uninstallCli()
        assertThat(confirmationCount).isEqualTo(1)
        assertThat(texts(panel)).doesNotContain("Uninstalling SonarQube CLI…", "Checking SonarQube CLI uninstall availability…")
        assertThat(coordinator.activeOperation()).isFalse()
        assertThat(coordinator.lastOutcome()).isNull()
        assertThat(uninstallButton().isEnabled).isTrue()
    }

    @Test
    fun `does not start uninstall while another CLI operation holds the lease`() {
        load(installedCli())
        assertThat(coordinator.acquire(project)).isTrue()

        uninstallButton().click()
        UIUtil.dispatchAllInvocationEvents()

        verify(backend, never()).uninstallCli()
        assertThat(confirmationCount).isEqualTo(1)
        assertThat(coordinator.activeOperation()).isTrue()
        assertThat(coordinator.lastOutcome()).isNull()
        coordinator.release()
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `shows uninstall progress then warnings and a refreshed install action`() {
        val refreshed = CompletableFuture<AiIntegrationSnapshot>()
        val uninstall = prepareUninstall(installedWithCliAgent(), refreshed)
        descendants(panel).filterIsInstance<JToggleButton>().first().doClick()

        uninstallButton().click()
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains("Uninstalling SonarQube CLI…")
        assertThat(uninstallButton().isEnabled).isFalse()
        assertThat(coordinator.activeOperation()).isTrue()
        assertThat(coordinator.execute(project, installedCli(), AiIntegrationsIntent.InstallCli)).isFalse()

        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "reset warning", "cleanup warning", null))
        UIUtil.dispatchAllInvocationEvents()
        assertThat(buttonTexts()).doesNotContain("Uninstall CLI…", "Integrate", "Sign in")
        assertThat(texts(panel)).contains(uninstallFeedback(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null)).summary)
            .doesNotContain("reset warning", "cleanup warning")
        refreshed.complete(removedCli())
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains(
            uninstallFeedback(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null)).summary
        ).doesNotContain("reset warning", "cleanup warning")
        assertThat(labels(panel)).doesNotContain("Reset output", "Reset errors and warnings")
        assertThat(buttonTexts()).contains("Install SonarQube CLI").doesNotContain("Uninstall CLI…")
        assertThat(coordinator.activeOperation()).isFalse()
    }

    @Test
    fun `reports a failed uninstall and keeps the installation available`() {
        val stillInstalled = CompletableFuture<AiIntegrationSnapshot>()
        val failure = prepareUninstall(refreshed = stillInstalled)
        uninstallButton().click()

        failure.complete(UninstallCliResponse(UninstallCliResponse.Status.FAILED, "", "reset failed", "SonarQube CLI reset failed."))
        UIUtil.dispatchAllInvocationEvents()
        stillInstalled.complete(installedCli())
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains("SonarQube CLI reset failed.").doesNotContain("reset failed")
        assertThat(buttonTexts()).contains("Uninstall CLI…").doesNotContain("Install SonarQube CLI")
    }

    @Test
    fun `closing the project does not free the uninstall lease until the backend finishes`() {
        val closingProject = mock(Project::class.java)
        var projectDisposed = false
        `when`(closingProject.isDisposed).thenAnswer { projectDisposed }
        val closingPanel = AiIntegrationsPanel()
        var refreshes = 0
        val localCoordinator = createCoordinator { refreshes++ }
        val local = createController(closingProject, closingPanel, localCoordinator) {
            assertThat(ApplicationManager.getApplication().isDispatchThread).isTrue()
            results += it
        }
        val uninstall = CompletableFuture<UninstallCliResponse>()
        `when`(backend.uninstallCli()).thenReturn(uninstall)
        load(installedCli(), local, closingProject)

        uninstallButton(closingPanel).click()
        UIUtil.dispatchAllInvocationEvents()
        assertThat(localCoordinator.activeOperation()).isTrue()

        projectDisposed = true
        Disposer.dispose(closingProject)

        assertThat(localCoordinator.activeOperation()).isTrue()
        assertThat(localCoordinator.lastOutcome()).isNull()
        assertThat(localCoordinator.execute(project, installedCli(), AiIntegrationsIntent.InstallCli)).isFalse()
        assertThat(localCoordinator.execute(project, installedCli(), AiIntegrationsIntent.AuthenticateCli)).isFalse()
        assertThat(localCoordinator.execute(project, installedCli(), AiIntegrationsIntent.IntegrateCli(AiAgent.CURSOR))).isFalse()

        CompletableFuture.runAsync {
            uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "reset output", "cleanup warning", null))
        }.join()
        UIUtil.dispatchAllInvocationEvents()

        assertThat(localCoordinator.activeOperation()).isFalse()
        assertThat(localCoordinator.lastOutcome()).isNull()
        assertThat(refreshes).isEqualTo(1)
        assertThat(results.single().resetOutput).isEqualTo("reset output")
        assertThat(results.single().cleanupWarnings).isEqualTo("cleanup warning")
        assertThat(localCoordinator.acquire(project)).isTrue()
        Disposer.dispose(local)
    }

    @Test
    fun `refresh during uninstall keeps the in-progress actions disabled`() {
        val afterUninstall = CompletableFuture<AiIntegrationSnapshot>()
        val installed = installedWithCliAgent()
        val uninstall = prepareUninstall(installed, afterUninstall)
        descendants(panel).filterIsInstance<JToggleButton>().first().doClick()
        assertThat(buttonTexts()).contains("Integrate")

        uninstallButton().click()
        refreshFromPanel()
        ApplicationManager.getApplication().messageBus.syncPublisher(CliOperationListener.TOPIC).operationFinished()
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).contains("Uninstalling SonarQube CLI…")
        assertCliActionsStayDisabled()
        verify(backend, times(2)).getAiIntegrationState(project, emptyList())
        assertThat(coordinator.activeOperation()).isTrue()

        uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.FAILED, "reset output", "reset failed", null))
        UIUtil.dispatchAllInvocationEvents()
        afterUninstall.complete(installed)
        UIUtil.dispatchAllInvocationEvents()

        verify(backend, times(3)).getAiIntegrationState(project, emptyList())
        assertThat(texts(panel)).contains(CLI_UNINSTALL_FAILED)
            .doesNotContain("reset output", "reset failed", "Uninstalling SonarQube CLI…")
        assertThat(uninstallButton().isEnabled).isTrue()
        assertThat(coordinator.activeOperation()).isFalse()

        refreshFromPanel()
        UIUtil.dispatchAllInvocationEvents()

        assertThat(texts(panel)).doesNotContain(CLI_UNINSTALL_FAILED)
        assertThat(refreshButton().isEnabled).isTrue()
    }

    @Test
    fun `finishing uninstall reloads each open project once`() {
        useQueuedRefresh()
        val panelB = AiIntegrationsPanel()
        val otherProject = mock(Project::class.java)
        extraController = createController(otherProject, panelB)
        val installed = installedCli()
        val afterUninstall = CompletableFuture<AiIntegrationSnapshot>()
        val uninstall = prepareUninstall(installed, afterUninstall)
        `when`(backend.getAiIntegrationState(otherProject, emptyList())).thenReturn(
            CompletableFuture.completedFuture(installed),
            afterUninstall
        )
        extraController!!.loadInitially()
        UIUtil.dispatchAllInvocationEvents()
        assertThat(buttonTexts(panelB)).contains("Uninstall CLI…")

        uninstallButton().click()
        CompletableFuture.runAsync {
            uninstall.complete(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "reset output", "cleanup warning", null))
        }.join()
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }
        verify(backend, times(3)).getAiIntegrationState(project, emptyList())
        verify(backend, times(2)).getAiIntegrationState(otherProject, emptyList())
        afterUninstall.complete(removedCli())
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }

        assertThat(buttonTexts(panelB))
            .contains("Install SonarQube CLI")
            .doesNotContain("Uninstall CLI…")
        assertThat(buttonTexts())
            .contains("Install SonarQube CLI")
            .doesNotContain("Uninstall CLI…")
        assertThat(texts(panel)).contains(uninstallFeedback(UninstallCliResponse(UninstallCliResponse.Status.UNINSTALLED, "", "", null)).summary)
            .doesNotContain("reset output", "cleanup warning")
    }

    @Test
    fun `a synchronous uninstall failure stays visible after the queued refresh`() {
        useQueuedRefresh()
        val installed = installedCli()
        val afterFailure = CompletableFuture<AiIntegrationSnapshot>()
        prepareUninstall(installed, afterFailure)
        `when`(backend.uninstallCli()).thenThrow(IllegalStateException("backend unavailable"))

        uninstallButton().click()
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }

        assertThat(texts(panel)).contains(CLI_UNINSTALL_FAILED).doesNotContain("backend unavailable")
        assertThat(results.single().cleanupWarnings).isEmpty()
        assertThat(results.single().backendMessage).isEqualTo("backend unavailable")
        verify(backend, times(3)).getAiIntegrationState(project, emptyList())

        afterFailure.complete(installed)
        repeat(4) { UIUtil.dispatchAllInvocationEvents() }

        assertThat(texts(panel)).contains(CLI_UNINSTALL_FAILED).doesNotContain("backend unavailable")
        assertThat(buttonTexts()).contains("Uninstall CLI…")
    }

    private fun createCoordinator(refreshViews: () -> Unit = ::publishOperationFinished) =
        CliOperationCoordinator(backend, { error("terminal unused") }, CliConnectionSelector(), { _, _, _ -> }, refreshViews)

    private fun createController(
        project: Project = this.project,
        panel: AiIntegrationsPanel = this.panel,
        coordinator: CliOperationCoordinator = this.coordinator,
        notifyUninstallResult: (CliUninstallFeedback.Finished) -> Unit = { results += it }
    ) = AiIntegrationsController(project, panel, backend, registry, coordinator, notifyUninstallResult) {
        confirmationCount++
        duringConfirmation()
        acceptUninstall
    }

    private fun useQueuedRefresh() {
        Disposer.dispose(controller)
        panel = AiIntegrationsPanel(registry)
        coordinator = createCoordinator {
            ApplicationManager.getApplication().invokeLater { publishOperationFinished() }
        }
        controller = createController()
    }

    private fun publishOperationFinished() =
        ApplicationManager.getApplication().messageBus.syncPublisher(CliOperationListener.TOPIC).operationFinished()

    private fun prepareUninstall(
        snapshot: AiIntegrationSnapshot = installedCli(),
        refreshed: CompletableFuture<AiIntegrationSnapshot>
    ): CompletableFuture<UninstallCliResponse> {
        load(snapshot)
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture.completedFuture(snapshot), refreshed)
        return CompletableFuture<UninstallCliResponse>().also { `when`(backend.uninstallCli()).thenReturn(it) }
    }

    private fun load(snapshot: AiIntegrationSnapshot, controller: AiIntegrationsController = this.controller, project: Project = this.project) {
        val loaded = CompletableFuture<AiIntegrationSnapshot>()
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(loaded)
        controller.loadInitially()
        loaded.complete(snapshot)
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun installedCli() = snapshot(emptyList()).copy(
        cli = CliState(CliInstallationStatus.INSTALLED, CliAuthenticationStatus.AUTHENTICATED, "1.0", null, null, uninstallAvailable = true)
    )

    private fun removedCli() = installedCli().let { it.copy(cli = it.cli.copy(installation = CliInstallationStatus.NOT_INSTALLED, uninstallAvailable = false)) }

    private fun installedWithCliAgent() = installedCli().copy(
        agents = listOf(AgentCapability(AiAgent.CLAUDE_CODE, setOf(AiAgentDetectionSource.IDE), true, false))
    )

    private fun assertCliActionsStayDisabled() {
        assertThat(buttonTexts()).doesNotContain("Integrate", "Install SonarQube CLI", "Sign in")
        assertThat(uninstallButton().isEnabled).isFalse()
    }

    private fun uninstallButton(container: Container = panel) = descendants(container).filterIsInstance<ActionButton>().single()

    private fun buttonTexts(container: Container = panel) = descendants(container).mapNotNull {
        when (it) {
            is JButton -> it.toolTipText ?: it.text
            is ActionButton -> it.presentation.text
            else -> null
        }
    }

    private fun refreshFromPanel() {
        refreshButton().doClick()
    }

    private fun refreshButton() = descendants(panel).filterIsInstance<JButton>().single { it.text == "Refresh" }

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
