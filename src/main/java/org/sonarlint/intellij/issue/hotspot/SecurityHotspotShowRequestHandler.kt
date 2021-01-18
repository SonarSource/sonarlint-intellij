/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.RetryAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingAssistant
import org.sonarlint.intellij.core.SecurityHotspotMatcher
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.issue.Location
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarlint.intellij.util.SonarLintUtils
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.WsHelperImpl
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper

const val NOTIFICATION_TITLE = "Error opening security hotspot"

const val FETCHING_HOTSPOT_ERROR_MESSAGE = "Cannot fetch hotspot details. Server is unreachable or credentials are invalid."
const val NOT_MATCHING_CODE_MESSAGE = "The local source code does not match the branch/revision analyzed by SonarQube"
const val FILE_NOT_FOUND_MESSAGE = "Cannot find hotspot file in the project."

open class SecurityHotspotShowRequestHandler(
        private val projectBindingAssistant: ProjectBindingAssistant = ProjectBindingAssistant("Opening Security Hotspot..."),
        private val wsHelper: WsHelper = WsHelperImpl(),
        private val telemetry: SonarLintTelemetry = getService(SonarLintTelemetry::class.java)
) {

    open fun open(projectKey: String, hotspotKey: String, serverUrl: String) {
        telemetry.showHotspotRequestReceived()
        doOpen(projectKey, hotspotKey, serverUrl)
    }

    private fun doOpen(projectKey: String, hotspotKey: String, serverUrl: String) {
        val (project, connection) = projectBindingAssistant.bind(projectKey, serverUrl) ?: return

        val balloonRetryAction = RetryAction { doOpen(projectKey, hotspotKey, serverUrl) }
        val remoteHotspot = fetchHotspot(connection, hotspotKey, projectKey) ?: run {
            showBalloon(project, FETCHING_HOTSPOT_ERROR_MESSAGE, balloonRetryAction)
            return
        }
        val localHotspot = SecurityHotspotMatcher(project).match(remoteHotspot)
        display(project, localHotspot, balloonRetryAction)
    }

    private fun fetchHotspot(connection: ServerConnection, hotspotKey: String, projectKey: String): RemoteHotspot? {
        val serverConfiguration = SonarLintUtils.getServerConfiguration(connection)
        val params = GetSecurityHotspotRequestParams(hotspotKey, projectKey)
        return wsHelper.getHotspot(serverConfiguration, params).orElse(null)
    }

    private fun display(project: Project, localHotspot: LocalHotspot, retryAction: RetryAction) {
        val highlighter = getService(project, EditorDecorator::class.java)
        getService(project, SonarLintToolWindow::class.java).show(localHotspot)
        if (localHotspot.primaryLocation.file != null) {
            openFile(project, localHotspot.primaryLocation)
            highlighter.highlight(localHotspot)
            if (localHotspot.primaryLocation.range == null) {
                showBalloon(project, NOT_MATCHING_CODE_MESSAGE, retryAction)
            }
        } else {
            showBalloon(project, FILE_NOT_FOUND_MESSAGE, retryAction)
        }
    }

    open fun showBalloon(project: Project, message: String, action: AnAction) {
        val notification = SecurityHotspotNotifications.GROUP.createNotification(
                NOTIFICATION_TITLE,
                message,
                NotificationType.ERROR, null)
        notification.isImportant = true
        notification.addAction(action)
        notification.notify(project)
    }

    private fun openFile(project: Project, location: Location) {
        OpenFileDescriptor(project, location.file!!, location.range?.startOffset ?: 0)
                .navigate(true)
    }
}
