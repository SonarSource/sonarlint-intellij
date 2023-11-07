/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.runOnPooledThread

class RefreshTaintVulnerabilitiesAction(text: String = "Refresh") : AbstractSonarAction(text, "Refresh taint vulnerabilities for open files", AllIcons.Actions.Refresh) {
  override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus) = getService(project, ProjectBindingManager::class.java).isBindingValid

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    runOnPooledThread(project) { getService(BackendService::class.java).refreshTaintVulnerabilities(project) }
  }
}
