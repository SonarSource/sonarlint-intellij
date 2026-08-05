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

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.currentfile.CurrentFilePanel
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.report.ReportPanel
import org.sonarlint.intellij.util.FixPromptGenerator

/**
 * Generates a structured prompt describing all currently displayed findings and copies it to the clipboard.
 * Works from both the Findings and Report tabs.
 */
class GenerateFixPromptAction : AnAction(
    "Generate Fix Prompt",
    "Generate a prompt to fix all displayed findings and copy it to the clipboard",
    SonarLintIcons.SPARKLE_GUTTER_ICON
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        runOnUiThread(project) {
            val findings = getFindingsFromSelectedTab(project) ?: return@runOnUiThread
            if (findings.isEmpty()) {
                return@runOnUiThread
            }

            val prompt = FixPromptGenerator.generate(findings)
            if (prompt.isBlank()) {
                return@runOnUiThread
            }

            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(prompt), null)

            val count = FixPromptGenerator.countFindings(findings)
            SonarLintProjectNotifications.get(project).displaySuccessfulNotification(
                "Fix prompt copied to clipboard ($count ${SonarLintUtils.pluralize("finding", count)})",
                NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IDE")
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }

        val findings = getFindingsFromSelectedTab(project)
        e.presentation.isEnabled = findings?.isNotEmpty() == true
    }

    private fun getFindingsFromSelectedTab(project: com.intellij.openapi.project.Project): FilteredFindings? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return null
        val selectedContent = toolWindow.contentManager.selectedContent ?: return null

        return when (val component = selectedContent.component) {
            is CurrentFilePanel -> component.getFilteredFindings()
            is ReportPanel -> component.getFilteredFindings()
            else -> null
        }
    }
}
