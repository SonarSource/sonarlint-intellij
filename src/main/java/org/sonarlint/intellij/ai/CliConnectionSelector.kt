/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ai

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.sonarlint.intellij.config.Settings.getSettingsFor

fun interface ConnectionChoiceUi {
    fun choose(project: Project, connections: List<IntegrationConnection>): String?
}

class IntellijConnectionChoiceUi : ConnectionChoiceUi {
    override fun choose(project: Project, connections: List<IntegrationConnection>): String? {
        val labels = connections.map { connection ->
            connection.organization?.let { "${connection.connectionId} ($it)" } ?: connection.connectionId
        }.toTypedArray()
        val selected = Messages.showChooseDialog(
            project,
            "Choose a SonarQube connection for the CLI sign-in.",
            "SonarQube CLI",
            null,
            labels,
            labels.firstOrNull()
        )
        return selected.takeIf { it >= 0 }?.let { connections[it].connectionId }
    }
}

class CliConnectionSelector(private val choiceUi: ConnectionChoiceUi = IntellijConnectionChoiceUi()) {
    fun select(project: Project, snapshot: AiIntegrationSnapshot): ConnectionSelection {
        val eligible = snapshot.connectionChoices
        val projectConnection = getSettingsFor(project).connectionName
        eligible.firstOrNull { it.connectionId == projectConnection }?.let {
            return ConnectionSelection.Selected(it.connectionId)
        }
        eligible.firstOrNull { it.connectionId == snapshot.recommendedConnectionId }?.let {
            return ConnectionSelection.Selected(it.connectionId)
        }
        if (eligible.size == 1) {
            return ConnectionSelection.Selected(eligible.single().connectionId)
        }
        if (eligible.isEmpty()) {
            return ConnectionSelection.InteractiveLogin
        }
        return choiceUi.choose(project, eligible)?.let(ConnectionSelection::Selected)
            ?: ConnectionSelection.Cancelled
    }
}

sealed interface ConnectionSelection {
    data class Selected(val connectionId: String) : ConnectionSelection
    data object InteractiveLogin : ConnectionSelection
    data object Cancelled : ConnectionSelection
}
