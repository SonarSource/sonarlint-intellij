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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.CliOperationListener
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus

class AiIntegrationsControllerTests : AbstractSonarLintLightTests() {
    private lateinit var backend: BackendService
    private lateinit var panel: AiIntegrationsPanel
    private lateinit var controller: AiIntegrationsController

    @BeforeEach
    fun setUpController() {
        backend = mock(BackendService::class.java)
        val registry = AiAgentRegistry(IdePluginDetector { false })
        panel = AiIntegrationsPanel(registry)
        controller = AiIntegrationsController(project, panel, backend, registry)
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
    fun `refreshes on CLI operation events until disposed`() {
        `when`(backend.getAiIntegrationState(project, emptyList())).thenReturn(CompletableFuture())
        val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(CliOperationListener.TOPIC)

        publisher.operationFinished()
        verify(backend, times(1)).getAiIntegrationState(project, emptyList())

        Disposer.dispose(controller)
        publisher.operationFinished()
        verify(backend, times(1)).getAiIntegrationState(project, emptyList())
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
