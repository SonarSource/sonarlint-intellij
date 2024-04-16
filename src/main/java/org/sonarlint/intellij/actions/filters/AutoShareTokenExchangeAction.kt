/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.AutomaticSharedConfigCreator
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto

class AutoShareTokenExchangeAction(
    text: String,
    private val connectionSuggestionDto: ConnectionSuggestionDto,
    private val project: Project,
    private val bindingMode: SonarLintUtils.BindingMode
) : AbstractSonarAction(
    text, null, null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val (isSQ, projectKey, connectionName) = getAutoShareConfigParams(connectionSuggestionDto)
        AutomaticSharedConfigCreator(projectKey, connectionName, isSQ, project, bindingMode).chooseResolution()
    }

    private fun getAutoShareConfigParams(uniqueSuggestion: ConnectionSuggestionDto): Triple<Boolean, String, String> {
        return if (uniqueSuggestion.connectionSuggestion.isRight) {
            Triple(
                false, uniqueSuggestion.connectionSuggestion.right.projectKey,
                uniqueSuggestion.connectionSuggestion.right.organization
            )
        } else {
            Triple(
                true, uniqueSuggestion.connectionSuggestion.left.projectKey,
                uniqueSuggestion.connectionSuggestion.left.serverUrl
            )
        }
    }
}
