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
package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.ToolWindowConstants
import javax.swing.DefaultComboBoxModel

/**
 * Manages display state, icons, and UI updates for CurrentFilePanel.
 * Handles MQR mode detection, empty states, and tree visibility.
 */
class CurrentFileDisplayManager(
    private val project: Project,
    private val filtersPanel: FiltersPanel
) {

    companion object {
        val MQR_IMPACTS = arrayOf("All", "Blocker", "High", "Medium", "Low", "Info")
        val STANDARD_SEVERITIES = arrayOf("All", "Blocker", "Critical", "Major", "Minor", "Info")
    }

    private var isMqrMode = false
    private var currentFile: VirtualFile? = null

    fun updateMqrMode(issues: List<LiveIssue>) {
        val newIsMqrMode = issues.any { it.getHighestImpact() != null }
        if (newIsMqrMode != isMqrMode) {
            isMqrMode = newIsMqrMode
            updateSeverityComboModel()
        }
    }

    private fun updateSeverityComboModel() {
        val newOptions = if (isMqrMode) MQR_IMPACTS else STANDARD_SEVERITIES
        val prev = filtersPanel.severityCombo.selectedItem as String?
        filtersPanel.severityCombo.setModel(DefaultComboBoxModel(newOptions))
        
        // Try to keep the same selection if possible
        if (prev != null) {
            for (i in newOptions.indices) {
                if (newOptions[i].equals(prev, ignoreCase = true)) {
                    filtersPanel.severityCombo.selectedIndex = i
                    break
                }
            }
        }
    }

    fun updateIcons(file: VirtualFile, filteredFindings: FilteredFindings) {
        this.currentFile = file
        updateToolWindowIcon(filteredFindings.issues)
        updateGutterIcons(filteredFindings.issues)
    }

    private fun updateToolWindowIcon(issues: List<LiveIssue>) {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowConstants.TOOL_WINDOW_ID)?.let { toolWindow ->
            val isEmpty = issues.none { !it.isResolved() }
            ApplicationManager.getApplication().assertIsDispatchThread()
            toolWindow.setIcon(if (isEmpty) 
                SonarLintIcons.SONARQUBE_FOR_INTELLIJ_EMPTY_TOOLWINDOW 
            else 
                SonarLintIcons.SONARQUBE_FOR_INTELLIJ_TOOLWINDOW
            )
        }
    }

    private fun updateGutterIcons(issues: List<LiveIssue>) {
        currentFile?.let { file ->
            val displayedIssues = if (getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()) {
                issues.filter { it.isOnNewCode() }
            } else {
                issues
            }
            getService(project, EditorDecorator::class.java).createGutterIconForIssues(file, displayedIssues)
        }
    }

    fun getCurrentFilterCriteria(): FilterCriteria {
        return FilterCriteria(
            severityFilter = filtersPanel.filterSeverity,
            statusFilter = filtersPanel.filterStatus,
            textFilter = filtersPanel.filterText,
            quickFixFilter = filtersPanel.quickFixCheckBox.isSelected,
            isMqrMode = isMqrMode
        )
    }

}
