package org.sonarlint.intellij.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.sonarlint.intellij.issue.hotspot.SecurityHotspotOpener
import org.sonarlint.intellij.util.SonarLintUtils

const val STATUS_ENDPOINT = "/sonarlint/api/status"
const val SHOW_HOTSPOT_ENDPOINT = "/sonarlint/api/hotspots/show"
const val PROJECT_KEY = "project"
const val HOTSPOT_KEY = "hotspot"
const val SERVER_URL = "server"

data class Status(val ideName: String, val description: String, val iconUrl: String)

class RequestProcessor(private val appInfo: ApplicationInfo = ApplicationInfo.getInstance(),
                       private val hotspotOpener: SecurityHotspotOpener = SecurityHotspotOpener()) {

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
        val ideIconUrl = SonarLintUtils.getIdeIcon()
        val description = ProjectManager.getInstance().openProjects.joinToString(", ") { it.name }
        val status = Status("${appInfo.versionName} ${appInfo.fullVersion}", description, ideIconUrl)
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
            hotspotOpener.open(projectKey, hotspotKey, serverUrl)
            // XXX take action based on the result
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

