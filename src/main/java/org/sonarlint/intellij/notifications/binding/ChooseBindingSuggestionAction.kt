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
package org.sonarlint.intellij.notifications.binding

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.config.global.ServerConnectionService
import org.sonarlint.intellij.ui.BindingSuggestionSelectionDialog

class ChooseBindingSuggestionAction(private val suggestedBindings: List<BindingSuggestion>) :
    NotificationAction("Configure binding") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val dialog = BindingSuggestionSelectionDialog(suggestedBindings)
        val acceptedSuggestion = dialog.chooseSuggestion() ?: return

        notification.expire()
        val project = e.project!!
        ServerConnectionService.getInstance().getServerConnectionByName(acceptedSuggestion.connectionId)
            .ifPresentOrElse(
                { connection ->
                    getService(project, ProjectBindingManager::class.java).bindTo(
                        connection,
                        acceptedSuggestion.projectKey,
                        emptyMap()
                    )
                },
                {
                    SonarLintConsole.get(project)
                        .debug("Cannot bind the project as the '${acceptedSuggestion.connectionId}' was deleted")
                })
    }
}
