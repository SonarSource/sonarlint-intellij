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

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.messages.CliOperationListener
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

class AiIntegrationsController @JvmOverloads constructor(
    private val project: Project,
    private val panel: AiIntegrationsPanel,
    private val backendService: BackendService = getService(BackendService::class.java),
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val cliCoordinator: CliOperationCoordinator = getService(CliOperationCoordinator::class.java),
    private val mcpCoordinator: McpConfigurationCoordinator = getService(McpConfigurationCoordinator::class.java)
) : Disposable {
    private val generation = AtomicLong()
    private val started = AtomicBoolean()
    private val disposed = AtomicBoolean()
    private var latestSnapshot: AiIntegrationSnapshot? = null

    init {
        panel.setRefreshListener(::refresh)
        panel.setIntentListener(::handleIntent)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(CliOperationListener.TOPIC, CliOperationListener { refresh() })
        mcpCoordinator.register(this, ::refresh)
    }

    fun loadInitially() {
        if (started.compareAndSet(false, true)) {
            refresh()
        }
    }

    fun refresh() {
        if (isDisposed()) {
            return
        }
        val requestedGeneration = generation.incrementAndGet()
        publish(requestedGeneration, AiIntegrationsPanelState.Loading)
        try {
            backendService.getAiIntegrationState(project, registry.detectedIdeAgents())
                .thenCompose(mcpCoordinator::inspectSnapshot)
                .whenComplete { snapshot, error ->
                    val state = if (error != null) {
                        AiIntegrationsPanelState.Error(userFacingMessage(error))
                    } else if (snapshot.agents.isEmpty()) {
                        AiIntegrationsPanelState.Empty(snapshot)
                    } else {
                        AiIntegrationsPanelState.Ready(snapshot)
                    }
                    publish(requestedGeneration, state)
                }
        } catch (error: Exception) {
            publish(requestedGeneration, AiIntegrationsPanelState.Error(userFacingMessage(error)))
        }
    }

    private fun publish(requestedGeneration: Long, state: AiIntegrationsPanelState) {
        runOnUiThread(project) {
            if (!isDisposed() && generation.get() == requestedGeneration) {
                latestSnapshot = when (state) {
                    is AiIntegrationsPanelState.Ready -> state.snapshot
                    is AiIntegrationsPanelState.Empty -> state.snapshot
                    else -> null
                }
                panel.render(state)
            }
        }
    }

    private fun handleIntent(intent: AiIntegrationsIntent) {
        when (intent) {
            AiIntegrationsIntent.Refresh -> refresh()
            AiIntegrationsIntent.OpenCliDocumentation -> BrowserUtil.browse(SonarLintDocumentation.Intellij.SONARQUBE_CLI_GUIDE_LINK)
            AiIntegrationsIntent.InstallCli,
            AiIntegrationsIntent.AuthenticateCli,
            is AiIntegrationsIntent.IntegrateCli -> latestSnapshot?.let { snapshot ->
                cliCoordinator.execute(project, snapshot, intent)
            }
            is AiIntegrationsIntent.SetUpMcp -> latestSnapshot?.let { snapshot ->
                mcpCoordinator.setUp(project, snapshot, intent.agent)
            }
            is AiIntegrationsIntent.OpenMcpConfiguration -> latestSnapshot?.let { snapshot ->
                mcpCoordinator.openConfiguration(project, intent.agent, snapshot)
            }
            AiIntegrationsIntent.OpenConnectionSettings -> mcpCoordinator.openConnectionSettings(project)
        }
    }

    private fun isDisposed() = disposed.get() || project.isDisposed || panel.isDisposed

    private fun userFacingMessage(error: Throwable): String {
        val cause = if (error is CompletionException && error.cause != null) error.cause!! else error
        return cause.message?.takeIf { it.isNotBlank() } ?: "Unable to load AI integrations."
    }

    override fun dispose() {
        disposed.set(true)
        generation.incrementAndGet()
        mcpCoordinator.unregister(this)
        panel.dispose()
    }
}
