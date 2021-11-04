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
package org.sonarlint.intellij.core

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

class ServerEventHandler {
    private var currentNotification: Notification? = null

    fun handle(message: String) {
        showBalloon(null, "Message received: $message", null)
    }

    private fun showBalloon(project: Project?, message: String, action: AnAction?) {
        currentNotification?.expire()
        val notification = ServerEventNotifications.GROUP.createNotification(
            "Server event received",
            message,
            NotificationType.INFORMATION, null
        )
        notification.isImportant = true
        action?.let { notification.addAction(it) }
        notification.notify(project)
        currentNotification = notification
    }
}
