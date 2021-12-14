/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.client.api.connected.ConnectionValidator
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper
import org.sonarsource.sonarlint.core.serverapi.system.ValidationResult
import java.util.concurrent.CompletableFuture

class ConnectionTestTask(private val server: ServerConnection) :
    Task.WithResult<ValidationResult?, Exception>(null, "Test Connection to " + if (server.isSonarCloud) "SonarCloud" else "SonarQube", true) {

    private lateinit var futureResult: CompletableFuture<ValidationResult>

    override fun compute(indicator: ProgressIndicator): ValidationResult? {
        indicator.text = "Connecting to " + server.hostUrl + "..."
        indicator.isIndeterminate = true
        val connectionValidator = ConnectionValidator(ServerApiHelper(server.endpointParams, server.httpClient))
        futureResult = connectionValidator.validateConnection()
        while (!futureResult.isDone) {
            Thread.sleep(500)
            if (indicator.isCanceled) {
                futureResult.cancel(true)
                return null
            }
        }
        return futureResult.get()
    }
}
