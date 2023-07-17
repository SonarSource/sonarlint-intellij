/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.ProgressUtils.waitForFuture
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse

class ConnectionTestTask(private val server: ServerConnection) :
    Task.WithResult<ValidateConnectionResponse?, Exception>(
        null, "Test Connection to " + if (server.isSonarCloud) "SonarCloud" else "SonarQube", true
    ) {

    override fun compute(indicator: ProgressIndicator): ValidateConnectionResponse? {
        indicator.text = "Connecting to " + server.hostUrl + "..."
        indicator.isIndeterminate = true
        return try {
            waitForFuture(indicator, SonarLintUtils.getService(BackendService::class.java).validateConnection(server))
        } catch (e: ProcessCanceledException) {
            null
        }
    }
}
