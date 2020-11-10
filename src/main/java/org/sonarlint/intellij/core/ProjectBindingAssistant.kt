/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.wizard.NewConnectionWizard
import org.sonarlint.intellij.ui.ProjectSelectionDialog

data class BoundProject(val project: Project, val connection: ServerConnection)

object ProjectBindingAssistant {

    fun bind(projectKey: String, serverUrl: String): BoundProject? {
        val connection = findOrCreateConnectionTo(serverUrl) ?: return null
        return findBoundProjectAmongOpened(projectKey, connection) ?: selectAndBindProject(projectKey, connection)
    }

    private fun findOrCreateConnectionTo(serverUrl: String): ServerConnection? {
        val connectionsToServer = getGlobalSettings().getConnectionsTo(serverUrl)
        // we pick the first connection but this could lead to issues later if there are several matches
        return connectionsToServer.getOrElse(0) { createConnectionTo(serverUrl) }
    }

    private fun createConnectionTo(serverUrl: String): ServerConnection? {
        val message = "No connections configured to $serverUrl."
        val result = Modal.showYesNoModalWindow(message, "Create connection")
        return if (result == Messages.OK) NewConnectionWizard().open(serverUrl) else null
    }

    private fun findBoundProjectAmongOpened(projectKey: String, connection: ServerConnection): BoundProject? {
        val project = ProjectManager.getInstance().openProjects
                .find { getSettingsFor(it).isBoundTo(projectKey, connection) }
        return if (project != null) BoundProject(project, connection) else null
    }

    private fun selectAndBindProject(projectKey: String, connection: ServerConnection): BoundProject? {
        val selectedProject = selectProject(projectKey, connection.hostUrl) ?: return null
        if (getSettingsFor(selectedProject).isBoundTo(projectKey, connection)) {
            return BoundProject(selectedProject, connection)
        }
        return bindProject(selectedProject, projectKey, connection)
    }

    private fun selectProject(projectKey: String, hostUrl: String): Project? {
        return if (shouldSelectProject(projectKey, hostUrl)) ProjectSelectionDialog().selectProject() else null
    }

    private fun shouldSelectProject(projectKey: String, hostUrl: String): Boolean {
        val message = "Cannot automatically find a project bound to:\n" +
                "  • Project: $projectKey\n" +
                "  • Server: $hostUrl\n" +
                "Please manually select a project."
        val result = Modal.showYesNoModalWindow(message, "Select project")
        return result == Messages.OK
    }

    private fun bindProject(project: Project, projectKey: String, connection: ServerConnection): BoundProject? {
        val result = Modal.showYesNoModalWindow("You are going to bind current project to ${connection.hostUrl}. Do you agree?", "Yes")
        if (result == Messages.OK) {
            getSettingsFor(project).bindTo(connection, projectKey)
            return BoundProject(project, connection)
        }
        return null
    }
}

object Modal {

    fun showYesNoModalWindow(message: String, yesText: String): Int {
        return Messages.showYesNoDialog(null, message, "Opening Security Hotspot...", yesText, "Cancel", Messages.getWarningIcon())
    }

}
