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
package org.sonarlint.intellij.util

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.ShowLogAction


fun displaySuccessfulNotification(project: Project, content: String, group: NotificationGroup) {
    val notification = group.createNotification(
        "",
        content,
        NotificationType.INFORMATION
    )
    notification.isImportant = true
    notification.notify(project)
}

fun displayErrorNotification(project: Project, content: String, group: NotificationGroup) {
    displayErrorNotification(project, "", content, group)
}

fun displayErrorNotification(project: Project, title: String, content: String, group: NotificationGroup) {
    val notification = group.createNotification(
        title, content, NotificationType.ERROR
    )
    notification.isImportant = true
    notification.addAction(ShowLogAction())
    notification.notify(project)
}
