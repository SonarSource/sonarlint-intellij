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
import com.intellij.openapi.project.ProjectManager
import org.sonarlint.intellij.actions.IssuesViewTabOpener
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.core.SecurityHotspotMatcher
import org.sonarlint.intellij.editor.SonarLintHighlighting
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.issue.IssueMatcher.NoMatchException
import org.sonarlint.intellij.util.SonarLintUtils
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.WsHelperImpl
import org.sonarsource.sonarlint.core.client.api.connected.GetSecurityHotspotRequestParams
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper
import java.util.*

enum class SecurityHotspotOpeningResult {
    NO_MATCHING_CONNECTION,
    PROJECT_NOT_FOUND,
    HOTSPOT_DETAILS_NOT_FOUND,
    LOCATION_NOT_MATCHING,
    SUCCESS
}

open class SecurityHotspotOpener(private val wsHelper: WsHelper, private val projectManager: ProjectManager) {

    constructor() : this(WsHelperImpl(), ProjectManager.getInstance())

    open fun open(projectKey: String, hotspotKey: String, serverUrl: String): SecurityHotspotOpeningResult {
        if (!getGlobalSettings().hasConnectionTo(serverUrl))
            return SecurityHotspotOpeningResult.NO_MATCHING_CONNECTION

        val project = getProject(projectKey, serverUrl) ?: return SecurityHotspotOpeningResult.PROJECT_NOT_FOUND

        val optionalRemoteHotspot = wsHelper.getHotspot(getConnectedServerConfig(project), GetSecurityHotspotRequestParams(hotspotKey, projectKey))
        if (!optionalRemoteHotspot.isPresent) {
            return SecurityHotspotOpeningResult.HOTSPOT_DETAILS_NOT_FOUND
        }
        val remoteHotspot = optionalRemoteHotspot.get()
        try {
            val localHotspot = SecurityHotspotMatcher(project).match(remoteHotspot)
            open(project, localHotspot.primaryLocation)
            val highlighter = getService(project, SonarLintHighlighting::class.java)
            highlighter.highlight(localHotspot)
            getService(project, IssuesViewTabOpener::class.java).show(localHotspot) { highlighter.removeHighlights() }
        } catch (e: NoMatchException) {
            return SecurityHotspotOpeningResult.LOCATION_NOT_MATCHING
        }
        return SecurityHotspotOpeningResult.SUCCESS
    }

    fun getProject(projectKey: String, serverUrl: String): Project? {
        return Arrays.stream(projectManager.openProjects)
                .filter {
                    val projectBindingManager = getService(it, ProjectBindingManager::class.java)
                    projectBindingManager.isBoundTo(serverUrl, projectKey)
                }
                .findFirst()
                .orElse(null)
    }

    @Throws(InvalidBindingException::class)
    private fun getConnectedServerConfig(project: Project): ServerConfiguration {
        val bindingManager = getService(project, ProjectBindingManager::class.java)
        val server = bindingManager.sonarQubeServer
        return SonarLintUtils.getServerConfiguration(server)
    }

    companion object {
        private fun open(project: Project, location: LocalHotspot.Location) {
            OpenFileDescriptor(project, location.file, location.range.startOffset)
                    .navigate(true)
        }
    }
}
