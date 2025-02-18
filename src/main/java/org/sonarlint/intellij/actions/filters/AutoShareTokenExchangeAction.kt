/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.actions.filters

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.core.ProjectBindingManager.BindingMode
import org.sonarlint.intellij.sharing.AutomaticSharedConfigCreator
import org.sonarsource.sonarlint.core.SonarCloudRegion
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto

class AutoShareTokenExchangeAction(
    text: String,
    private val connectionSuggestionDto: ConnectionSuggestionDto,
    private val project: Project,
    private val bindingMode: BindingMode,
) : NotificationAction(text) {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        val (isSQ, projectKey, orgOrServerUrl, region) = getAutoShareConfigParams(connectionSuggestionDto)
        AutomaticSharedConfigCreator(projectKey, orgOrServerUrl, isSQ, project, bindingMode, region).chooseResolution()
    }

    private fun getAutoShareConfigParams(uniqueSuggestion: ConnectionSuggestionDto): Quadruple {
        return if (uniqueSuggestion.connectionSuggestion.isRight) {
            Quadruple(
                false, uniqueSuggestion.connectionSuggestion.right.projectKey,
                uniqueSuggestion.connectionSuggestion.right.organization,
                SonarCloudRegion.valueOf(uniqueSuggestion.connectionSuggestion.right.region.name)
            )
        } else {
            Quadruple(
                true, uniqueSuggestion.connectionSuggestion.left.projectKey,
                uniqueSuggestion.connectionSuggestion.left.serverUrl, null
            )
        }
    }
}
