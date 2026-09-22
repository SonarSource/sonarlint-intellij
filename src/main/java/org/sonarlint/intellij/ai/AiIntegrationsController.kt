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
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation

class AiIntegrationsController @JvmOverloads constructor(
    private val project: Project,
    private val panel: AiIntegrationsPanel,
    private val backendService: BackendService = getService(BackendService::class.java),
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val environment: AiIntegrationEnvironment = IntellijAiIntegrationEnvironment()
) : Disposable {
    private val generation = AtomicLong()
    private val started = AtomicBoolean()
    private val disposed = AtomicBoolean()

    init {
        panel.setIntentListener(::handleIntent)
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
        if (environment.isRemote()) {
            publish(requestedGeneration, AiIntegrationsPanelState.Remote)
            return
        }
        publish(requestedGeneration, AiIntegrationsPanelState.Loading)
        CompletableFuture.supplyAsync(registry::detectedIdeAgents, AppExecutorUtil.getAppExecutorService())
            .thenCompose { detectedAgents -> backendService.getAiIntegrationState(project, detectedAgents) }
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
    }

    private fun publish(requestedGeneration: Long, state: AiIntegrationsPanelState) {
        ApplicationManager.getApplication().invokeLater({
            if (!isDisposed() && generation.get() == requestedGeneration) {
                panel.render(state)
            }
        }, project.disposed)
    }

    private fun handleIntent(intent: AiIntegrationsIntent) {
        when (intent) {
            AiIntegrationsIntent.Refresh -> refresh()
            AiIntegrationsIntent.OpenDocumentation -> BrowserUtil.browse(SonarLintDocumentation.Intellij.AI_INTEGRATIONS_LINK)
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
        panel.dispose()
    }
}
