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

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JSeparator
import javax.swing.JToggleButton

class CurrentFileSummaryPanel(
    issuesSelectionChanged: (Boolean) -> Unit,
    hotspotsSelectionChanged: (Boolean) -> Unit,
    taintsSelectionChanged: (Boolean) -> Unit,
    dependencyRisksSelectionChanged: (Boolean) -> Unit,
    toggleFilterBtnClicked: (Boolean) -> Unit
) : JBPanel<CurrentFileSummaryPanel>(HorizontalLayout(10)) {

    private val issuesSummaryButton = SummaryButton("Issue", "Issues", issuesSelectionChanged, "Show/hide issues")
    private val hotspotsSummaryButton = SummaryButton("Security Hotspot", "Security Hotspots", hotspotsSelectionChanged, "Show/hide security hotspots")
    private val taintsSummaryButton = SummaryButton("Taint Vulnerability", "Taint Vulnerabilities", taintsSelectionChanged, "Show/hide taint vulnerabilities")
    private val dependencyRisksSummaryButton = SummaryButton("Dependency Risk", "Dependency Risks", dependencyRisksSelectionChanged, "Show/hide dependency risks")
    private val toggleFilterBtn = JToggleButton(AllIcons.General.Filter)

    init {
        border = JBUI.Borders.empty(10)

        toggleFilterBtn.apply {
            isFocusPainted = false
            isContentAreaFilled = false
            isOpaque = false
            toolTipText = "Show Filters"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 2, 2, 2),
                BorderFactory.createLineBorder(JBColor.LIGHT_GRAY, 1, true)
            )
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    if (!toggleFilterBtn.isSelected) {
                        toggleFilterBtn.setBackground(UIUtil.getPanelBackground().brighter())
                        toggleFilterBtn.setOpaque(true)
                    }
                }
                override fun mouseExited(e: MouseEvent?) {
                    if (!toggleFilterBtn.isSelected) {
                        toggleFilterBtn.setOpaque(false)
                        toggleFilterBtn.setBackground(null)
                    }
                }
            })
            addChangeListener {
                toggleFilterBtnClicked(toggleFilterBtn.isSelected)
                toggleFilterBtn.toolTipText = if (toggleFilterBtn.isSelected) "Hide Filters" else "Show Filters"
                toggleFilterBtn.background = if (toggleFilterBtn.isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getPanelBackground()
            }
        }

        add(issuesSummaryButton)
        add(hotspotsSummaryButton)
        add(taintsSummaryButton)
        add(dependencyRisksSummaryButton)
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(toggleFilterBtn)
    }

    fun updateIssues(count: Int, uiModel: SummaryUiModel) = issuesSummaryButton.update(count, uiModel)
    fun updateHotspots(count: Int, uiModel: SummaryUiModel) = hotspotsSummaryButton.update(count, uiModel)
    fun updateTaints(count: Int, uiModel: SummaryUiModel) = taintsSummaryButton.update(count, uiModel)
    fun updateDependencyRisks(count: Int, uiModel: SummaryUiModel) = dependencyRisksSummaryButton.update(count, uiModel)

    fun setHotspotsEnabled(isSelected: Boolean) = hotspotsSummaryButton.setEnabled(isSelected)
    fun setTaintsEnabled(isSelected: Boolean) = taintsSummaryButton.setEnabled(isSelected)
    fun setDependencyRisksEnabled(isSelected: Boolean) = dependencyRisksSummaryButton.setEnabled(isSelected)

    fun areIssuesEnabled() = issuesSummaryButton.isSelected()
    fun areHotspotsEnabled() = hotspotsSummaryButton.isSelected()
    fun areTaintsEnabled() = taintsSummaryButton.isSelected()
    fun areDependencyRisksEnabled() = dependencyRisksSummaryButton.isSelected()

}
