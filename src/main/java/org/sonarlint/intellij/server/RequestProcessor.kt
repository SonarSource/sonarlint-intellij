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
package org.sonarlint.intellij.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.ProjectManager
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder

const val STATUS_ENDPOINT = "/sonarlint/api/status"
const val SHOW_HOTSPOT_ENDPOINT = "/sonarlint/api/hotspots/show"
const val PROJECT_KEY = "project"
const val HOTSPOT_KEY = "hotspot"
const val SERVER_URL = "server"

data class Status(val ideName: String, val description: String)

class RequestProcessor(private val appInfo: ApplicationInfo = ApplicationInfo.getInstance(),
                       private val orchestrator: SecurityHotspotOrchestrator = SecurityHotspotOrchestrator()) {

    fun processRequest(request: Request): Response {
        if (request.path == STATUS_ENDPOINT && request.method == HttpMethod.GET) {
            return getStatusData()
        }
        if (request.path == SHOW_HOTSPOT_ENDPOINT && request.method == HttpMethod.GET) {
            return processOpenInIdeRequest(request)
        }
        return BadRequest("Invalid path or method.")
    }

    private fun getStatusData(): Response {
        var description = appInfo.fullVersion
        val edition = ApplicationNamesInfo.getInstance().editionName
        if (edition != null) {
            description += " ($edition)"
        }
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isNotEmpty()) {
            description += " - " + openProjects.joinToString(", ") { it.name }
        }
        val status = Status(appInfo.versionName, description)
        return Success(ObjectMapper().writeValueAsString(status))
    }

    private fun missingParameter(parameterName: String): BadRequest {
        return BadRequest("The '$parameterName' parameter is not specified")
    }

    private fun processOpenInIdeRequest(request: Request): Response {
        val projectKey = request.getParameter(PROJECT_KEY) ?: return missingParameter(PROJECT_KEY)
        val hotspotKey = request.getParameter(HOTSPOT_KEY) ?: return missingParameter(HOTSPOT_KEY)
        val serverUrl = request.getParameter(SERVER_URL) ?: return missingParameter(SERVER_URL)

        ApplicationManager.getApplication().invokeLater {
            orchestrator.open(projectKey, hotspotKey, serverUrl)
        }
        return Success()
    }
}

open class Response

data class Success(val body: String? = null) : Response()

data class BadRequest(val message: String) : Response()

data class Request(val uri: String, val method: HttpMethod) {
    val path = uri.substringBefore('?')
    private val parameters = QueryStringDecoder(uri).parameters()

    fun getParameter(parameterName: String): String? {
        return parameters[parameterName]?.get(0)
    }
}

