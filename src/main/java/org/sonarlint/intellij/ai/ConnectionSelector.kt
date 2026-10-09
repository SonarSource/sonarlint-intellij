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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import org.sonarlint.intellij.config.Settings.getSettingsFor

fun interface ConnectionChoiceUi {
    fun choose(project: Project, connections: List<IntegrationConnection>): String?
}

class IntellijConnectionChoiceUi : ConnectionChoiceUi {
    override fun choose(project: Project, connections: List<IntegrationConnection>): String? =
        ConnectionChoiceDialog(
            project,
            "Choose a SonarQube connection for CLI authentication.",
            "SonarQube CLI",
            connections
        ).choose()
}

class ConnectionSelector(private val choiceUi: ConnectionChoiceUi) {
    fun select(project: Project, eligible: List<IntegrationConnection>, recommendedConnectionId: String?): ConnectionSelection {
        val projectConnection = getSettingsFor(project).connectionName
        eligible.firstOrNull { it.connectionId == projectConnection }?.let {
            return ConnectionSelection.Selected(it.connectionId)
        }
        eligible.firstOrNull { it.connectionId == recommendedConnectionId }?.let {
            return ConnectionSelection.Selected(it.connectionId)
        }
        if (eligible.size == 1) {
            return ConnectionSelection.Selected(eligible.single().connectionId)
        }
        if (eligible.isEmpty()) {
            return ConnectionSelection.Missing
        }
        return choiceUi.choose(project, eligible)?.let(ConnectionSelection::Selected)
            ?: ConnectionSelection.Cancelled
    }
}

sealed interface ConnectionSelection {
    data class Selected(val connectionId: String) : ConnectionSelection
    data object Missing : ConnectionSelection
    data object Cancelled : ConnectionSelection
}

internal class ConnectionChoiceDialog(
    project: Project,
    private val message: String,
    dialogTitle: String,
    private val connections: List<IntegrationConnection>
) : DialogWrapper(project) {
    private val choiceList = JBList(connections.map { connection ->
        connection.organization?.let { "${connection.connectionId} ($it)" } ?: connection.connectionId
    })

    init {
        title = dialogTitle
        init()
        if (connections.isEmpty()) {
            okAction.isEnabled = false
        } else {
            choiceList.selectedIndex = 0
        }
    }

    fun choose(): String? = if (showAndGet()) connections.getOrNull(choiceList.selectedIndex)?.connectionId else null

    override fun createCenterPanel(): JComponent {
        choiceList.visibleRowCount = choiceList.model.size.coerceIn(2, 8)
        val panel = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(8)))
        panel.add(JBLabel(message), BorderLayout.NORTH)
        panel.add(JBScrollPane(choiceList), BorderLayout.CENTER)
        return panel
    }
}
