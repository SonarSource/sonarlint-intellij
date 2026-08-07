/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.currentfile.CurrentFilePanel
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.report.ReportPanel
import org.sonarlint.intellij.util.FixPromptBuilder

/**
 * Generates a prompt containing all currently displayed findings and copies it to the clipboard.
 * Works for both the Current File and Report tabs.
 */
class GenerateFixPromptAction : AnAction(
    ACTION_TEXT,
    ACTION_DESCRIPTION,
    SonarLintIcons.SPARKLE_GUTTER_ICON
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val findingsContext = getDisplayedFindingsContext(project) ?: return
        if (findingsContext.findings.isEmpty()) return

        val prompt = FixPromptBuilder.build(findingsContext.findings, findingsContext.contextLabel)
        val selection = StringSelection(prompt)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)

        val findingCount = countFindings(findingsContext.findings)
        SonarLintProjectNotifications.projectLessNotification(
            "Fix prompt copied to clipboard",
            "Generated a prompt for $findingCount finding(s). Paste it into your favorite AI agent to fix the issues.",
            NotificationType.INFORMATION
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || !project.isInitialized || project.isDisposed) {
            e.presentation.isEnabled = false
            return
        }
        val findings = getDisplayedFindingsContext(project)?.findings
        e.presentation.isEnabled = findings?.isNotEmpty() == true
        // Icon-only in the tool window toolbar to avoid pushing actions off-screen on narrow layouts.
        if (TOOL_WINDOW_ID == e.place) {
            e.presentation.setText(null)
        } else {
            e.presentation.setText(ACTION_TEXT)
        }
    }

    private fun countFindings(findings: FilteredFindings): Int {
        return findings.issues.size + findings.hotspots.size + findings.taints.size + findings.dependencyRisks.size
    }

    private data class FindingsContext(val findings: FilteredFindings, val contextLabel: String)

    private fun getDisplayedFindingsContext(project: Project): FindingsContext? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return null
        val selectedContent = toolWindow.contentManager.selectedContent ?: return null

        return when (val component = selectedContent.component) {
            is CurrentFilePanel -> FindingsContext(component.getDisplayedFindings(), "Current File tab")
            is ReportPanel -> FindingsContext(component.getDisplayedFindings(), "Report tab")
            else -> null
        }
    }

    companion object {
        private const val ACTION_TEXT = "Copy Fix Prompt"
        private const val ACTION_DESCRIPTION =
            "Generate a prompt to fix all displayed findings and copy it to the clipboard"
    }
}
