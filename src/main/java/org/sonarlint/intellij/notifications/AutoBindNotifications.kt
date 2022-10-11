/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable

object AutoBindNotifications {
    val GROUP = NotificationGroupManager.getInstance()
        .getNotificationGroup("SonarLint: Auto-binding")

    fun sendNotification(project: Project) {
        val notification = GROUP.createNotification(
            "SonarLint: auto-binding",
            "Do you want to bind ide project '${project.name}' to project 'FIXME' of SonarQube server 'connectionId'?",
            NotificationType.INFORMATION, null
        )
        notification.isImportant = false
        notification.addAction(AutoBindAction(project))
        notification.addAction(ManualBindAction(project))
        notification.addAction(NoBindAction(project))
        notification.notify(project)
    }
}

class AutoBindAction(project: Project) : NotificationAction("Configure binding") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        TODO("Not yet implemented")
    }
}

class ManualBindAction(project: Project) : NotificationAction("Choose manually") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        val configurable = SonarLintProjectConfigurable(e.project)
        ShowSettingsUtil.getInstance().editConfigurable(e.project, configurable)

    }
}

class NoBindAction(project: Project) : NotificationAction("Don't ask again") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        TODO("Remember user decision")
    }
}