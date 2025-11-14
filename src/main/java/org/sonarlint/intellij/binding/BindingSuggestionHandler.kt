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
package org.sonarlint.intellij.binding

import com.intellij.openapi.module.Module
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto

object BindingSuggestionHandler {

    fun findOverriddenModules(suggestions: List<ClientBindingSuggestion>, projectBinding: ClientBindingSuggestion): Map<Module, String> {
        return suggestions
            .filter { it.module != null }
            .filter {
                val projectConn = projectBinding.suggestion.connectionSuggestion
                val moduleConn = it.suggestion.connectionSuggestion
                when {
                    projectConn.isLeft && moduleConn.isLeft -> {
                        val projectSuggestion = projectConn.left
                        val moduleSuggestion = moduleConn.left
                        projectSuggestion.serverUrl == moduleSuggestion.serverUrl && projectSuggestion.projectKey != moduleSuggestion.projectKey
                    }

                    projectConn.isRight && moduleConn.isRight -> {
                        val projectSuggestion = projectConn.right
                        val moduleSuggestion = moduleConn.right
                        projectSuggestion.organization == moduleSuggestion.organization && projectSuggestion.projectKey != moduleSuggestion.projectKey
                    }

                    else -> false
                }
            }
            .associate {
                val module = it.module!!
                if (it.suggestion.connectionSuggestion.isRight) {
                    module to it.suggestion.connectionSuggestion.right.projectKey
                } else {
                    module to it.suggestion.connectionSuggestion.left.projectKey
                }
            }
    }

    fun getAutoShareConfigParams(uniqueSuggestion: ConnectionSuggestionDto): Triple<String, String, String> {
        return if (uniqueSuggestion.connectionSuggestion.isRight) {
            Triple(
                "SonarQube Cloud organization", uniqueSuggestion.connectionSuggestion.right.projectKey,
                uniqueSuggestion.connectionSuggestion.right.organization)
        } else {
            Triple(
                "SonarQube Server instance", uniqueSuggestion.connectionSuggestion.left.projectKey,
                uniqueSuggestion.connectionSuggestion.left.serverUrl)
        }
    }

}
