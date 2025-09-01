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
package org.sonarlint.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.currentfile.CurrentFilePanel
import org.sonarlint.intellij.ui.report.ReportPanel

/**
 * Action to collapse all trees in the SonarLint tool window.
 * Works for both Current File and Report tabs.
 */
class CollapseAllTreesAction : AnAction("Collapse All", "Collapse all finding trees", AllIcons.Actions.Collapseall), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Run on UI thread to ensure proper tree access
        runOnUiThread(project) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return@runOnUiThread
            val selectedContent = toolWindow.contentManager.selectedContent ?: return@runOnUiThread
            
            when (val component = selectedContent.component) {
                is CurrentFilePanel -> {
                    collapseCurrentFileTrees(component)
                }
                is ReportPanel -> {
                    collapseReportTrees(component)
                }
            }
        }
    }
    
    private fun collapseCurrentFileTrees(panel: CurrentFilePanel) {
        panel.collapseAllTrees()
    }
    
    private fun collapseReportTrees(panel: ReportPanel) {
        panel.collapseAllTrees()
    }
}
