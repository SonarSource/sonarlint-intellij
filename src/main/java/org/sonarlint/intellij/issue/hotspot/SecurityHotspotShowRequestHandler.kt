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
package org.sonarlint.intellij.issue.hotspot

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingAssistant
import org.sonarlint.intellij.core.SecurityHotspotMatcher
import org.sonarlint.intellij.editor.SonarLintHighlighting
import org.sonarlint.intellij.util.SonarLintUtils
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.WsHelperImpl
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot

const val FETCHING_HOTSPOT_ERROR_MESSAGE = "Can not fetch hotspot details. Server is unreachable or credentials are not valid."
const val NOT_MATCHING_CODE_MESSAGE = "Local code does not match. Make sure it corresponds to the code that had been analyzed."

open class SecurityHotspotShowRequestHandler {

    open fun open(projectKey: String, hotspotKey: String, serverUrl: String) {
        val (project, connection) = ProjectBindingAssistant.bind(projectKey, serverUrl) ?: return

        val remoteHotspot = fetchHotspot(connection, hotspotKey, projectKey) ?: run {
            Notifier.showBalloon(FETCHING_HOTSPOT_ERROR_MESSAGE, project)
            return
        }
        val localHotspot = SecurityHotspotMatcher(project).match(remoteHotspot)
        display(project, localHotspot)
    }

    private fun fetchHotspot(connection: ServerConnection, hotspotKey: String, projectKey: String): RemoteHotspot? {
        val serverConfiguration = SonarLintUtils.getServerConfiguration(connection)
        val params = GetSecurityHotspotRequestParams(hotspotKey, projectKey)
        return WsHelperImpl().getHotspot(serverConfiguration, params).orElse(null)
    }

    private fun display(project: Project, localHotspot: LocalHotspot) {
        val highlighter = getService(project, SonarLintHighlighting::class.java)
        getService(project, SonarLintToolWindow::class.java).show(localHotspot) { highlighter.removeHighlights() }
        if (localHotspot.primaryLocation.file != null) {
            open(project, localHotspot.primaryLocation)
            highlighter.highlight(localHotspot)
            if (localHotspot.primaryLocation.range == null) {
                Notifier.showBalloon(NOT_MATCHING_CODE_MESSAGE, project)
            }
        }
        else {
            Notifier.showBalloon(NOT_MATCHING_CODE_MESSAGE, project)
        }
    }

    private fun open(project: Project, location: Location) {
        OpenFileDescriptor(project, location.file!!, location.range?.startOffset ?: 0)
                .navigate(true)
    }
}

object Notifier {

    private val OPEN_IN_IDE_GROUP = NotificationGroup.balloonGroup("SonarLint: Open in IDE")

    fun showBalloon(message: String, project: Project) {
        val balloon = OPEN_IN_IDE_GROUP.createNotification(
                "<b>Open in IDE event</b>",
                message,
                NotificationType.ERROR,
                NotificationListener.UrlOpeningListener(true))
        balloon.isImportant = true
        balloon.notify(project)
    }

}
