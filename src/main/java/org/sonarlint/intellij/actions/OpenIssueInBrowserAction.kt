/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY
import org.sonarsource.sonarlint.core.serverapi.UrlUtils

class OpenIssueInBrowserAction : AbstractSonarAction(
  "Open In Browser",
  "Open issue in browser interface of SonarQube or SonarCloud",
  null
) {

  override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus): Boolean {
    return e.getData(TAINT_VULNERABILITY_DATA_KEY) != null
  }

  override fun updatePresentation(e: AnActionEvent, project: Project) {
    val serverConnection = serverConnection(project) ?: return
    e.presentation.text = "Open in " + serverConnection.productName
    e.presentation.icon = serverConnection.product.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val issue = e.getData(TAINT_VULNERABILITY_DATA_KEY)
    val key = issue?.key() ?: return
    val localFile = issue.file() ?: return
    val localFileModule = ModuleUtil.findModuleForFile(localFile, project) ?: return
    val serverUrl = serverConnection(project)?.hostUrl ?: return
    val projectKey = getService(localFileModule, ModuleBindingManager::class.java).resolveProjectKey() ?: return
    BrowserUtil.browse(buildLink(serverUrl, projectKey, key))
    getService(SonarLintTelemetry::class.java).taintVulnerabilitiesInvestigatedRemotely()
  }

  private fun buildLink(serverUrl: String, projectKey: String, issueKey: String): String {
    val urlEncodedProjectKey = UrlUtils.urlEncode(projectKey)
    val urlEncodedIssueKey = UrlUtils.urlEncode(issueKey)
    return "$serverUrl/project/issues?id=$urlEncodedProjectKey&open=$urlEncodedIssueKey"
  }

  private fun serverConnection(project: Project): ServerConnection? = getService(project, ProjectBindingManager::class.java).tryGetServerConnection().orElse(null)
}
