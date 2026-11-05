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
package org.sonarlint.intellij.notifications

import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.config.global.credentials.CredentialOperationRunner
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.wizard.ServerConnectionWizard
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

class ConfigureNotificationsAction(private val connectionName: String, private val project: Project) : NotificationAction("Configure") {

    private var request: CredentialOperationRunner.Request? = null
    private var generation = 0
    private var activeWizard: ServerConnectionWizard? = null

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val frame = WindowManager.getInstance().getFrame(e.project) ?: return
        runOnUiThread(project) {
            val connectionToEdit = Settings.getGlobalSettings().serverConnections.find { it.name == connectionName }
            if (connectionToEdit != null) {
                if (activeWizard != null) return@runOnUiThread
                request?.let { Disposer.dispose(it) }
                val currentGeneration = ++generation
                request = CredentialOperationRunner.get(project, frame.rootPane, connectionToEdit,
                    { currentGeneration == generation && !notification.isExpired &&
                        Settings.getGlobalSettings().serverConnections.any { it === connectionToEdit } },
                    { credentials -> showWizard(connectionToEdit, credentials, currentGeneration) },
                    { error ->
                        SonarLintConsole.get(project).error("Failed to edit connection notifications: ${error.message}", error)
                        notification.expire()
                    }, {})
            } else {
                SonarLintConsole.get(project).error("Unable to find connection with name: $connectionName")
                notification.expire()
            }
        }
    }

    private fun showWizard(connection: ServerConnection,
                           credentials: Either<
                               TokenDto,
                               UsernamePasswordDto>, currentGeneration: Int) {
        val wizard = ServerConnectionWizard.forNotificationsEdition(connection, credentials)
        activeWizard = wizard
        var showing = true
        val lifetime = Disposable {
            ApplicationManager.getApplication().invokeLater({ if (showing) wizard.cancel() }, ModalityState.any())
        }
        Disposer.register(project, lifetime)
        try {
            if (wizard.showAndGet() && !project.isDisposed && currentGeneration == generation) {
                val connections = Settings.getGlobalSettings().serverConnections.toMutableList()
                val index = connections.indexOfFirst { it === connection }
                if (index >= 0) {
                    connections[index] = wizard.connection
                    Settings.getGlobalSettings().serverConnections = connections
                }
            }
        } finally {
            showing = false
            Disposer.dispose(lifetime)
            activeWizard = null
        }
    }

}
