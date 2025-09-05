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
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.sharing.AutomaticSharedConfigCreator
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion

class AutoShareTokenExchangeAction(
    text: String,
    private val connectionSuggestionDto: ConnectionSuggestionDto,
    private val project: Project,
    private val overridesPerModule: Map<Module, String>
) : NotificationAction(text) {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        val (isSQ, projectKey, orgOrServerUrl, region, origin) = getAutoShareConfigParams(connectionSuggestionDto)
        AutomaticSharedConfigCreator(projectKey, orgOrServerUrl, isSQ, project, overridesPerModule, region, origin).chooseResolution()
    }

    private fun getAutoShareConfigParams(uniqueSuggestion: ConnectionSuggestionDto): AutoShareConfigParams {
        return if (uniqueSuggestion.connectionSuggestion.isRight) {
            AutoShareConfigParams(
                false, uniqueSuggestion.connectionSuggestion.right.projectKey,
                uniqueSuggestion.connectionSuggestion.right.organization,
                SonarCloudRegion.valueOf(uniqueSuggestion.connectionSuggestion.right.region.name),
                uniqueSuggestion.origin
            )
        } else {
            AutoShareConfigParams(
                true, uniqueSuggestion.connectionSuggestion.left.projectKey,
                uniqueSuggestion.connectionSuggestion.left.serverUrl, null,
                uniqueSuggestion.origin
            )
        }
    }

    data class AutoShareConfigParams(
        val isSQ: Boolean,
        val projectKey: String,
        val orgOrServerUrl: String,
        val region: SonarCloudRegion?,
        val origin: BindingSuggestionOrigin
    )

}
