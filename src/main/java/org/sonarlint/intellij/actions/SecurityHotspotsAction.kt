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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.ProjectBindingManager

class SecurityHotspotsAction(text: String = "Display all hotspots") : AbstractSonarAction(text, "Display all security hotspots", AllIcons.Actions.Show)  {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project?: return
        val sonarLintToolWindow = SonarLintUtils.getService(project, SonarLintToolWindow::class.java)
        sonarLintToolWindow.renderFindings(sonarLintToolWindow.hotspotList)

    }
    override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus) = SonarLintUtils.getService(
        project,
        ProjectBindingManager::class.java
    ).isBindingValid
}
