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
package org.sonarlint.intellij.callable

import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.analysis.AnalysisCallback
import org.sonarlint.intellij.analysis.AnalysisIntermediateResult
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.analysis.OnTheFlyFindingsHolder
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.ShowFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.notifications.ClearSecurityHotspotsFiltersAction
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

class ShowFindingCallable<T: Finding>(private val project: Project, onTheFlyFindingsHolder: OnTheFlyFindingsHolder, private val showFinding: ShowFinding<T>) : AnalysisCallback {

    private val updateOnTheFlyFindingsCallable =
        UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder)

    override fun onError(e: Throwable) {
        updateOnTheFlyFindingsCallable.onError(e)
    }

    override fun onIntermediateResult(intermediateResult: AnalysisIntermediateResult) {
        updateOnTheFlyFindingsCallable.onIntermediateResult(intermediateResult)
    }

    override fun onSuccess(analysisResult: AnalysisResult) {
        updateOnTheFlyFindingsCallable.onSuccess(analysisResult)
        showFinding()
    }

    private fun showFinding() {
        runOnUiThread(project) {
            val toolWindow = getService(project, SonarLintToolWindow::class.java)
            when (showFinding.type) {
                LiveSecurityHotspot::class.java -> showSecurityHotspot(toolWindow)
                LiveIssue::class.java -> showIssue(toolWindow)
                LocalTaintVulnerability::class.java -> showTaintVulnerability(toolWindow)
                else -> SonarLintProjectNotifications.get(project)
                    .notifyUnableToOpenFinding(
                        "finding",
                        "The finding could not be detected by SonarQube for IntelliJ in the current code."
                    )
            }
        }
    }

    private fun showSecurityHotspot(toolWindow: SonarLintToolWindow) {
        toolWindow.openSecurityHotspotsTab()
        toolWindow.bringToFront()

        val found = getService(project, SonarLintToolWindow::class.java).doesSecurityHotspotExist(showFinding.findingKey)
        if (!found) {
            SonarLintProjectNotifications.get(project)
                .notifyUnableToOpenFinding(
                    "Security Hotspot",
                    "The Security Hotspot could not be detected by SonarQube for IntelliJ in the current code."
                )
        } else {
            val selected =
                getService(project, SonarLintToolWindow::class.java).trySelectSecurityHotspot(showFinding.findingKey)
            if (!selected) {
                SonarLintProjectNotifications.get(project)
                    .notifyUnableToOpenFinding(
                        "Security Hotspot",
                        "The Security Hotspot could not be opened by SonarQube for IntelliJ due to the applied filters.",
                        ClearSecurityHotspotsFiltersAction(showFinding.findingKey)
                    )
            }
        }
    }

    private fun showIssue(toolWindow: SonarLintToolWindow) {
        toolWindow.openCurrentFileTab()
        toolWindow.bringToFront()

        getService(project, SonarLintToolWindow::class.java).trySelectIssue(showFinding)
    }

    private fun showTaintVulnerability(toolWindow: SonarLintToolWindow) {
        toolWindow.openTaintVulnerabilityTab()
        toolWindow.bringToFront()

        getService(project, SonarLintToolWindow::class.java).trySelectTaintVulnerability(showFinding)
    }
}
