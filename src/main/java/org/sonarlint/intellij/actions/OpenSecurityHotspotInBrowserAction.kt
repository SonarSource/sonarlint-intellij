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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot

class OpenSecurityHotspotInBrowserAction : AbstractSonarAction(
  "Open In Browser",
  "Open Security Hotspot in browser",
  null
) {
  companion object {
    val SECURITY_HOTSPOT_DATA_KEY = DataKey.create<LiveSecurityHotspot>("sonarlint_security_hotspot")
  }

  override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus): Boolean {
    return e.getData(SECURITY_HOTSPOT_DATA_KEY) != null &&
        e.getData(SECURITY_HOTSPOT_DATA_KEY)?.serverFindingKey != null
  }

  override fun updatePresentation(e: AnActionEvent, project: Project) {
    val serverConnection = serverConnection(project) ?: return
    e.presentation.text = "Open in " + serverConnection.productName
    e.presentation.description = "Open Security Hotspot in browser interface of " + serverConnection.productName
    e.presentation.icon = serverConnection.product.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val securityHotspot = e.getData(SECURITY_HOTSPOT_DATA_KEY)
    val key = securityHotspot?.serverFindingKey ?: return
    val localFile = securityHotspot.file()
    val localFileModule = ModuleUtil.findModuleForFile(localFile, project) ?: return
    getService(BackendService::class.java).openHotspotInBrowser(localFileModule, key)
  }

  private fun serverConnection(project: Project): ServerConnection? = getService(project, ProjectBindingManager::class.java).tryGetServerConnection().orElse(null)
}
