/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.ProgressUtils
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto

class GenerateTokenAction(
    private val project: Project,
    private val connection: ServerConnection
) : NotificationAction("Generate token") {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val serverUrl = connection.hostUrl

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating token...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val future = getService(BackendService::class.java).helpGenerateUserToken(serverUrl)
                    val result = ProgressUtils.waitForFuture(indicator, future)

                    val token = result.token
                    if (token != null) {
                        saveTokenAndNotify(token, notification)
                        return
                    }

                    SonarLintConsole.get(project).info("Token generation was cancelled or no token was received")
                } catch (ex: Exception) {
                    SonarLintConsole.get(project).error("Unable to generate token", ex)
                    Messages.showErrorDialog(project, ex.message, "Unable to Generate Token")
                }
            }
        })
    }

    private fun saveTokenAndNotify(token: String, originalNotification: Notification) {
        try {
            getService(CredentialsService::class.java).saveCredentials(connection.name, Either.forLeft(TokenDto(token)))

            originalNotification.expire()

            SonarLintProjectNotifications.get(project).simpleNotification(
                null,
                "Token for connection '${connection.name}' has been successfully updated",
                NotificationType.INFORMATION
            )
        } catch (ex: Exception) {
            SonarLintConsole.get(project).error("Unable to save token", ex)

            ex.message?.let {
                SonarLintProjectNotifications.get(project).displayErrorNotification(
                    "Unable to Save Token", it,
                    NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IDE")
                )
            } ?: run {
                SonarLintProjectNotifications.get(project).displayErrorNotification(
                    "Automatic token update failed. Please go to Settings to update your token manually.",
                    NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IDE")
                )
            }
        }
    }

}
