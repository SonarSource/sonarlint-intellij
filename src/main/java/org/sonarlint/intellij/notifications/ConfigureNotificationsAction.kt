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

    init {
        Disposer.register(project, Disposable {
            request?.let { Disposer.dispose(it) }
            ApplicationManager.getApplication().invokeLater({ activeWizard?.cancel() }, ModalityState.any())
        })
    }

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
                    { credentials ->
                        val wizard = ServerConnectionWizard.forNotificationsEdition(connectionToEdit, credentials)
                        activeWizard = wizard
                        try {
                            if (wizard.showAndGet() && !project.isDisposed && currentGeneration == generation) {
                                val serverConnections = Settings.getGlobalSettings().serverConnections.toMutableList()
                                val index = serverConnections.indexOfFirst { it === connectionToEdit }
                                if (index >= 0) {
                                    serverConnections[index] = wizard.connection
                                    Settings.getGlobalSettings().serverConnections = serverConnections
                                }
                            }
                        } finally {
                            activeWizard = null
                        }
                    },
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

}
