/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.ui.report

import javax.swing.DefaultComboBoxModel
import org.sonarlint.intellij.ui.filter.FilterCriteria
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarlint.intellij.ui.filter.FiltersPanel
import org.sonarlint.intellij.ui.filter.MqrImpactFilter
import org.sonarlint.intellij.ui.filter.SeverityFilter

/**
 * Manages display state, UI updates, and visual feedback for the Report panel.
 * 
 * <h3>Design & Architecture:</h3>
 * This service acts as a coordinator between the UI components and the underlying data state,
 * managing visual feedback elements like filter controls and display modes. It implements
 * a reactive pattern where display updates are triggered by data changes.
 * 
 * <h3>Core Responsibilities:</h3>
 * - MQR Mode Management: Automatically detects and switches between MQR and standard severity modes
 * - Filter Control Updates: Dynamically updates filter panel controls based on current context
 * - Visual State Coordination: Ensures UI consistency across different components
 */
class ReportDisplayManager(private val filtersPanel: FiltersPanel) {

    private var isMqrMode = true
    
    init {
        updateSeverityComboModel()
    }

    fun updateMqrMode(findings: FilteredFindings) {
        if (findings.issues.isEmpty() && findings.hotspots.isEmpty()) {
            return
        }
        val newIsMqrMode = findings.issues.any { it.isMqrMode } || findings.hotspots.any { it.isMqrMode }
        if (newIsMqrMode != isMqrMode) {
            isMqrMode = newIsMqrMode
            updateSeverityComboModel()
        }
    }

    private fun updateSeverityComboModel() {
        val newOptions = if (isMqrMode) MqrImpactFilter.values() else SeverityFilter.values()
        filtersPanel.severityCombo.setModel(DefaultComboBoxModel(newOptions))
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
