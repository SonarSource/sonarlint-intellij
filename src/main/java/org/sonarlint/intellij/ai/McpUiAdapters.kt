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

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.projectLessNotification
import org.sonarlint.intellij.util.computeInEDT

interface McpUiAdapter {
    fun chooseConnection(project: Project, connections: List<IntegrationConnection>): String?

    fun confirmWithoutToken(project: Project): Boolean

    fun openConfiguration(project: Project, path: Path)

    fun openConnectionSettings(project: Project)

    fun showMessage(project: Project, message: String, type: NotificationType)
}

class IntellijMcpUiAdapter : McpUiAdapter {
    private val jsonFactory = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .build().factory

    override fun chooseConnection(project: Project, connections: List<IntegrationConnection>): String? =
        ConnectionChoiceDialog(
            project,
            "Choose a SonarQube connection for the MCP server.",
            "SonarQube MCP Server",
            connections
        ).choose()

    override fun confirmWithoutToken(project: Project): Boolean = ApplicationManager.getApplication().computeInEDT {
        Messages.showYesNoDialog(
            project,
            "No token is saved for this connection. The MCP server may not work until you add a valid token to its configuration.",
            "Set Up SonarQube MCP Server",
            "Proceed Anyway",
            "Cancel",
            Messages.getWarningIcon()
        ) == Messages.YES
    }

    override fun openConfiguration(project: Project, path: Path) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let { file ->
            val offset = FileDocumentManager.getInstance().getDocument(file)?.text?.let(::sonarQubeOffset) ?: 0
            OpenFileDescriptor(project, file, offset).navigate(true)
        }
    }

    private fun sonarQubeOffset(content: String): Int = runCatching {
        jsonFactory.createParser(content).use { parser ->
            while (parser.nextToken() != null) {
                val section = parser.parsingContext.parent
                if (parser.currentToken == JsonToken.FIELD_NAME && parser.currentName == "sonarqube" &&
                    section?.currentName in listOf("mcpServers", "servers") && section?.parent?.inRoot() == true) {
                    return@use parser.currentTokenLocation().charOffset.toInt()
                }
            }
            0
        }
    }.getOrDefault(0)

    override fun openConnectionSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SonarLintGlobalConfigurable::class.java)
    }

    override fun showMessage(project: Project, message: String, type: NotificationType) {
        projectLessNotification("SonarQube MCP Server", message, type)
    }
}
