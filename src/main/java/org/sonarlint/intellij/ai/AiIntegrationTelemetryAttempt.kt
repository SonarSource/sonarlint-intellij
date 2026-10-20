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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import org.sonarlint.intellij.messages.AiIntegrationListener
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus

internal class AiIntegrationTelemetryAttempt(
    private val telemetry: SonarLintTelemetry,
    private val action: AiIntegrationAction,
    private val agent: AiAgent? = null
) {
    private val finished = AtomicBoolean()

    init {
        report(AiIntegrationActionStatus.STARTED)
    }

    fun finish(status: AiIntegrationActionStatus): Boolean {
        if (!finished.compareAndSet(false, true)) return false
        report(status)
        return true
    }

    private fun report(status: AiIntegrationActionStatus) = safelyReportAiTelemetry {
        telemetry.aiIntegrationAction(action, status, agent)
    }
}

internal fun safelyReportAiTelemetry(report: () -> Unit) {
    try {
        report()
    } catch (_: Exception) {
        Logger.getInstance(AiIntegrationTelemetryAttempt::class.java).debug("Unable to report AI integrations telemetry")
    }
}

internal fun publishAiIntegrationChange(origin: Project?) {
    try {
        ApplicationManager.getApplication().messageBus.syncPublisher(AiIntegrationListener.TOPIC).stateChanged(origin)
    } catch (_: Exception) {
        Logger.getInstance(AiIntegrationTelemetryAttempt::class.java).debug("Unable to refresh AI integrations")
    }
}

internal fun isAiIntegrationCancellation(error: Throwable?): Boolean = when (error) {
    is CancellationException, is ProcessCanceledException -> true
    is CompletionException -> isAiIntegrationCancellation(error.cause)
    else -> false
}
