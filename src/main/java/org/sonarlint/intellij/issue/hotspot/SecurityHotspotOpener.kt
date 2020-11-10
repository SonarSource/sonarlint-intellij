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

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingAssistant
import org.sonarlint.intellij.core.SecurityHotspotMatcher
import org.sonarlint.intellij.editor.SonarLintHighlighting
import org.sonarlint.intellij.issue.IssueMatcher.NoMatchException
import org.sonarlint.intellij.util.SonarLintUtils
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.WsHelperImpl
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot

open class SecurityHotspotOpener {

    open fun open(projectKey: String, hotspotKey: String, serverUrl: String) {
        val (project, connection) = ProjectBindingAssistant.bind(projectKey, serverUrl) ?: return

        val remoteHotspot = fetchHotspot(connection, hotspotKey, projectKey) ?: return //TODO show balloon
        try {
            val localHotspot = SecurityHotspotMatcher(project).match(remoteHotspot)
            open(project, localHotspot.primaryLocation)
            val highlighter = getService(project, SonarLintHighlighting::class.java)
            highlighter.highlight(localHotspot)
            getService(project, SonarLintToolWindow::class.java).show(localHotspot) { highlighter.removeHighlights() }
        } catch (e: NoMatchException) {
            // TODO display balloon
        }
    }

    private fun fetchHotspot(connection: ServerConnection, hotspotKey: String, projectKey: String): RemoteHotspot? {
        val optionalRemoteHotspot = WsHelperImpl().getHotspot(SonarLintUtils.getServerConfiguration(connection), GetSecurityHotspotRequestParams(hotspotKey, projectKey))
        return optionalRemoteHotspot.orElse(null)
    }

    private fun open(project: Project, location: LocalHotspot.Location) {
        OpenFileDescriptor(project, location.file, location.range.startOffset)
                .navigate(true)
    }
}
