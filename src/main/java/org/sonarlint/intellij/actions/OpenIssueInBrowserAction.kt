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
package org.sonarlint.intellij.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.SonarLintStatus
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.util.StringUtils

class OpenIssueInBrowserAction : AbstractSonarAction(
  "Open In Browser",
  "Open issue in browser interface of SonarQube or SonarCloud",
  null
) {
  companion object {
    val TAINT_VULNERABILITY_DATA_KEY = DataKey.create<LocalTaintVulnerability>("sonarlint_taint_vulnerability")
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val serverConnection = serverConnection(project) ?: return
    super.update(e)
    e.presentation.text = "Open in " + serverConnection.productName
    e.presentation.icon = serverConnection.productIcon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val issue = e.getData(TAINT_VULNERABILITY_DATA_KEY)
    val key = issue?.key() ?: return
    val serverUrl = serverConnection(project)?.hostUrl ?: return
    BrowserUtil.browse(buildLink(serverUrl, getSettingsFor(project).projectKey!!, key))
    getService(SonarLintTelemetry::class.java).taintVulnerabilitiesInvestigatedRemotely()
  }

  override fun isEnabled(e: AnActionEvent, project: Project, status: SonarLintStatus): Boolean {
    return e.getData(TAINT_VULNERABILITY_DATA_KEY) != null
  }

  private fun buildLink(serverUrl: String, projectKey: String, issueKey: String): String {
    val urlEncodedProjectKey = StringUtils.urlEncode(projectKey)
    val urlEncodedIssueKey = StringUtils.urlEncode(issueKey)
    return "$serverUrl/project/issues?id=$urlEncodedProjectKey&open=$urlEncodedIssueKey"
  }

  private fun serverConnection(project: Project): ServerConnection? = getService(project, ProjectBindingManager::class.java).tryGetServerConnection().orElse(null)
}