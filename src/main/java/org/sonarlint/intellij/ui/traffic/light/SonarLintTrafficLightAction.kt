/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.ui.traffic.light

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import javax.swing.JComponent
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesCache

private val DASHBOARD_MODEL = Key<SonarLintDashboardModel>("DASHBOARD_MODEL")

class SonarLintTrafficLightAction(private val editor: Editor) : AbstractSonarAction(), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return SonarLintTrafficLightWidget(this, presentation, place, editor)
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        presentation.getClientProperty(DASHBOARD_MODEL)?.let { (component as SonarLintTrafficLightWidget).refresh(it) }
    }

    override fun updatePresentation(e: AnActionEvent, project: Project) {
        if (project.isDisposed) {
            return
        }
        e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { file ->
            val isAlive = getService(BackendService::class.java).isAlive()
            val presentation = e.presentation
            val isFocusOnNewCode = getService(project, CleanAsYouCodeService::class.java).shouldFocusOnNewCode()
            val relevantIssues = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder.getIssuesForFile(file)
                .filter { !it.isResolved() && (!isFocusOnNewCode || it.isOnNewCode()) }
            val relevantSecurityHotspots =
                getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder.getSecurityHotspotsForFile(file)
                    .filter { !it.isResolved() && (!isFocusOnNewCode || it.isOnNewCode()) }
            val relevantTaintVulnerabilitiesCount =
                getService(project, TaintVulnerabilitiesCache::class.java).getTaintVulnerabilitiesForFile(file)
                    .filter { !it.isResolved() && (!isFocusOnNewCode || it.isOnNewCode()) }
            val model =
                SonarLintDashboardModel(
                    isAlive,
                    relevantIssues.size,
                    relevantSecurityHotspots.size,
                    relevantTaintVulnerabilitiesCount.size,
                    isFocusOnNewCode
                )
            presentation.putClientProperty(DASHBOARD_MODEL, model)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        editor.project?.let { getService(it, SonarLintToolWindow::class.java).openOrCloseCurrentFileTab() }
    }

}
