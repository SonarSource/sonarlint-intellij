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
import javax.swing.DefaultComboBoxModel
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.ToolWindowConstants
import org.sonarlint.intellij.ui.filter.FilterCriteria
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarlint.intellij.ui.filter.FiltersPanel
import org.sonarlint.intellij.ui.filter.MqrImpactFilter
import org.sonarlint.intellij.ui.filter.SeverityFilter

/**
 * Manages display state, UI updates, and visual feedback for the Current File panel.
 * 
 * <h3>Design & Architecture:</h3>
 * This service acts as a coordinator between the UI components and the underlying data state,
 * managing visual feedback elements like icons, filter controls, and display modes. It implements
 * a reactive pattern where display updates are triggered by data changes.
 * 
 * <h3>Core Responsibilities:</h3>
 * - MQR Mode Management:</strong> Automatically detects and switches between MQR and standard severity modes
 * - Filter Control Updates:</strong> Dynamically updates filter panel controls based on current context
 * - Icon Management:</strong> Updates tool window and editor gutter icons based on finding states
 * - Visual State Coordination:</strong> Ensures UI consistency across different components
 */
class CurrentFileDisplayManager(
    private val project: Project,
    private val filtersPanel: FiltersPanel
) {

    private var isMqrMode = true
    private var currentFile: VirtualFile? = null

    fun updateMqrMode(findings: FilteredFindings) {
        if (findings.issues.isEmpty() && findings.hotspots.isEmpty() && findings.taints.isEmpty()) {
            return
        }
        val newIsMqrMode = findings.issues.any { it.isMqrMode } || findings.hotspots.any { it.isMqrMode } || findings.taints.any { it.isMqrMode() }
        if (newIsMqrMode != isMqrMode) {
            isMqrMode = newIsMqrMode
            updateSeverityComboModel()
        }
    }

    private fun updateSeverityComboModel() {
        val newOptions = if (isMqrMode) MqrImpactFilter.values() else SeverityFilter.values()
        filtersPanel.severityCombo.setModel(DefaultComboBoxModel(newOptions))
    }

    fun updateIcons(filteredFindings: FilteredFindings) {
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
            isMqrMode = isMqrMode,
            findingsScope = filtersPanel.findingsScope
        )
    }

}
