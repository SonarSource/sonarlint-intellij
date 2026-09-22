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

import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.projectLessNotification

interface McpUiAdapter {
    fun chooseConnection(project: Project, connections: List<IntegrationConnection>): String?

    fun confirmExternalTakeover(project: Project): Boolean

    fun openConfiguration(project: Project, path: Path)

    fun openConnectionSettings(project: Project)

    fun showMessage(project: Project, message: String, type: NotificationType)
}

class IntellijMcpUiAdapter : McpUiAdapter {
    override fun chooseConnection(project: Project, connections: List<IntegrationConnection>): String? {
        val values = connections.map { it.connectionId }.toTypedArray()
        val selected = Messages.showChooseDialog(
            project,
            "Choose a SonarQube connection for the MCP server.",
            "SonarQube MCP Server",
            null,
            values,
            values.firstOrNull()
        )
        return selected.takeIf { it >= 0 }?.let { connections[it].connectionId }
    }

    override fun confirmExternalTakeover(project: Project): Boolean = Messages.showYesNoDialog(
        project,
        "Only the SonarQube entry will be replaced. Unrelated entries in this configuration file will be preserved.",
        "Set Up SonarQube MCP Server Again",
        Messages.getWarningIcon()
    ) == Messages.YES

    override fun openConfiguration(project: Project, path: Path) {
        if (!Files.exists(path)) {
            return
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let { file ->
            OpenFileDescriptor(project, file).navigate(true)
        }
    }

    override fun openConnectionSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SonarLintGlobalConfigurable::class.java)
    }

    override fun showMessage(project: Project, message: String, type: NotificationType) {
        projectLessNotification("SonarQube MCP Server", message, type)
    }
}

class McpConnectionSelector(private val ui: McpUiAdapter) {
    fun select(project: Project, snapshot: AiIntegrationSnapshot): McpConnectionSelection {
        val connections = snapshot.connectionChoices
        val projectConnection = getSettingsFor(project).connectionName
        connections.firstOrNull { it.connectionId == projectConnection }?.let {
            return McpConnectionSelection.Selected(it.connectionId)
        }
        if (connections.size == 1) {
            return McpConnectionSelection.Selected(connections.single().connectionId)
        }
        if (connections.isEmpty()) {
            return McpConnectionSelection.Missing
        }
        return ui.chooseConnection(project, connections)?.let(McpConnectionSelection::Selected)
            ?: McpConnectionSelection.Cancelled
    }
}

sealed interface McpConnectionSelection {
    data class Selected(val connectionId: String) : McpConnectionSelection
    data object Missing : McpConnectionSelection
    data object Cancelled : McpConnectionSelection
}
