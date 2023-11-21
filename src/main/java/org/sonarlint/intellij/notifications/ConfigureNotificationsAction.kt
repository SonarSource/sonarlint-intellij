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
package org.sonarlint.intellij.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.config.global.ServerConnectionService
import org.sonarlint.intellij.config.global.wizard.ServerConnectionWizard
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.runOnPooledThread

class ConfigureNotificationsAction(private val connectionName: String, private val project: Project) : NotificationAction("Configure") {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        WindowManager.getInstance().getFrame(project) ?: return
        runOnPooledThread(project) {
            ServerConnectionService.getInstance().getServerConnectionWithAuthByName(connectionName)
                    .ifPresentOrElse({
                        runOnUiThread(project) {
                            val wizard = ServerConnectionWizard.forNotificationsEdition(it)
                            if (wizard.showAndGet()) {
                                runOnPooledThread(project) { ServerConnectionService.getInstance().replaceConnection(wizard.connectionWithAuth) }
                            }
                        }
                    }, {
                        SonarLintConsole.get(project).error("Unable to find connection with name: $connectionName")
                        notification.expire()
                    })
        }
    }
}
