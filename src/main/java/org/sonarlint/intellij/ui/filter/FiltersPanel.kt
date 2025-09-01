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
package org.sonarlint.intellij.ui.filter

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JSeparator
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import org.sonarlint.intellij.common.util.SonarLintUtils.getService

enum class ScopeMode(val displayName: String, val tooltip: String) {
    CURRENT_FILE("Current File", "Show issues/hotspots/taints for the current file only"),
    OPEN_FILES("All Files", "Show all cached issues/hotspots/taints from analyzed files + project-wide SCA")
}

/**
 * Filter panel component that provides various filtering and sorting controls for findings.
 * 
 * <h3>Design & Architecture:</h3>
 * This panel follows a reactive design pattern where filter changes immediately trigger callbacks to update
 * the displayed findings. It contains multiple filter types:
 *
 * - Scope Filter:</strong> Toggle between current file only or all open files + project-wide findings (Current File tab only)
 * - Search Filter:</strong> Text-based search across rule names, messages, and file names
 * - Severity Filter:</strong> Filters by issue severity/impact levels (adapts to MQR/standard mode)
 * - Status Filter:</strong> Filters by resolution status (All/Open/Resolved) - only visible in connected mode
 * - Quick Fix Filter:</strong> Shows only findings with available quick fixes or AI code fixes
 * - Sorting Controls:</strong> Allows sorting by date, impact, rule key, or line number
 * - Focus on New Code:</strong> Toggle to show only findings in new code areas
 * 
 * <h3>Visibility Management:</h3>
 * The panel uses conditional visibility for certain filters:
 * - Scope filter is only shown in Current File tab (not in Report tab)
 * - Status filter is only shown in connected mode (when bound to SonarQube/SonarCloud)
 * - The entire panel can be hidden/shown via the summary panel toggle
 */
class FiltersPanel(
    private val onFilterChanged: () -> Unit,
    private val onSortingChanged: (SortMode) -> Unit,
    private val onFocusOnNewCodeChanged: (Boolean) -> Unit,
    private val onScopeModeChanged: () -> Unit = {},
    private val showScopeFilter: Boolean = true
) : JBPanel<FiltersPanel>() {

    val scopeLabel = JBLabel("Scope:")
    val scopeCombo = ComboBox(ScopeMode.values())
    val searchLabel = JBLabel("Search:")
    val searchField = SearchTextField()
    val severityLabel = JBLabel("Severity:")
    val severityCombo: ComboBox<Any> = ComboBox(MqrImpactFilter.values())
    val statusLabel = JBLabel("Status:")
    val statusCombo = ComboBox(StatusFilter.values())
    val quickFixLabel = JBLabel("Fix suggestion:")
    val quickFixCheckBox = JBCheckBox()
    val sortLabel = JBLabel("Sort by:")
    val sortCombo = ComboBox(SortMode.values())
    val focusOnNewCodeLabel = JBLabel("New code:")
    val focusOnNewCodeCheckBox = JBCheckBox()
    val cleanFiltersBtn = JButton("Default")
    private lateinit var statusSeparator: JSeparator
    private val statusSpacingComponents = mutableListOf<Component>()

    var scopeMode = ScopeMode.CURRENT_FILE
    var filterText = ""
    var filterSeverity: SeverityImpactFilter = SeverityImpactFilter.Severity(SeverityFilter.NO_FILTER)
    var filterStatus = StatusFilter.OPEN
    private var sortMode = getService(FilterSettingsService::class.java).getDefaultSortMode()

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = JBUI.Borders.empty(0, 8, 8, 8)
        isVisible = false

        initComponents()
        addComponents()
    }

    private fun initComponents() {
        scopeLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        scopeCombo.apply {
            toolTipText = "Filter scope: current file only or all files (Issues And Security Hotspots are displayed for opened files)"
            maximumSize = Dimension(120, 30)
            selectedItem = ScopeMode.CURRENT_FILE
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is ScopeMode) {
                        text = value.displayName
                        toolTipText = value.tooltip
                    }
                    return this
                }
            }
            addActionListener { _ ->
                scopeMode = scopeCombo.selectedItem as ScopeMode
                onScopeModeChanged()
                onFilterChanged()
            }
        }

        searchLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        searchField.apply {
            toolTipText = "Search by rule name, message or file"
            textEditor.columns = 12
            maximumSize = Dimension(160, 30)
            preferredSize = Dimension(120, 30)
            addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    filterText = searchField.text
                    onFilterChanged()
                }
                override fun removeUpdate(e: DocumentEvent) {
                    filterText = searchField.text
                    onFilterChanged()
                }
                override fun changedUpdate(e: DocumentEvent) {
                    filterText = searchField.text
                    onFilterChanged()
                }

            })
        }

        severityLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        severityCombo.apply {
            toolTipText = "Filter by severity"
            maximumSize = Dimension(90, 30)
            addActionListener { _ ->
                filterSeverity = when (severityCombo.selectedItem) {
                    is SeverityFilter -> SeverityImpactFilter.Severity(severityCombo.selectedItem as SeverityFilter)
                    is MqrImpactFilter -> SeverityImpactFilter.MqrImpact(severityCombo.selectedItem as MqrImpactFilter)
                    else -> SeverityImpactFilter.Severity(SeverityFilter.NO_FILTER)
                }
                onFilterChanged()
            }
        }

        statusLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        statusCombo.apply {
            toolTipText = "Filter by status"
            maximumSize = Dimension(90, 30)
            selectedItem = StatusFilter.OPEN
            addActionListener { _ ->
                filterStatus = statusCombo.selectedItem as StatusFilter
                onFilterChanged()
            }
        }

        quickFixLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        quickFixCheckBox.apply {
            maximumSize = Dimension(24, 30)
            addActionListener { _ -> onFilterChanged() }
        }

        sortLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        sortCombo.apply {
            toolTipText = "Sort findings by"
            maximumSize = Dimension(160, 30)
            selectedItem = sortMode
            addActionListener { _ ->
                sortMode = sortCombo.selectedItem as SortMode
                getService(FilterSettingsService::class.java).setDefaultSortMode(sortMode)
                onSortingChanged(sortMode)
                onFilterChanged()
            }
        }

        focusOnNewCodeLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        focusOnNewCodeCheckBox.apply {
            maximumSize = Dimension(24, 30)
            toolTipText = "Show only findings in new code"
            addActionListener { _ -> 
                onFocusOnNewCodeChanged(focusOnNewCodeCheckBox.isSelected)
                onFilterChanged()
            }
        }

        cleanFiltersBtn.apply {
            toolTipText = "Reset all filters"
            maximumSize = Dimension(60, 30)
            addActionListener { _ ->
                if (showScopeFilter) {
                    scopeMode = ScopeMode.CURRENT_FILE
                    scopeCombo.selectedItem = ScopeMode.CURRENT_FILE
                    onScopeModeChanged()
                }
                filterText = ""
                searchField.text = ""
                filterSeverity = when (filterSeverity) {
                    is SeverityImpactFilter.MqrImpact -> SeverityImpactFilter.MqrImpact(MqrImpactFilter.NO_FILTER)
                    else -> SeverityImpactFilter.Severity(SeverityFilter.NO_FILTER)
                }
                severityCombo.selectedItem = SeverityFilter.NO_FILTER
                filterStatus = StatusFilter.OPEN
                statusCombo.selectedItem = StatusFilter.OPEN
                quickFixCheckBox.isSelected = false
                sortMode = SortMode.DATE
                sortCombo.selectedItem = SortMode.DATE
                focusOnNewCodeCheckBox.isSelected = false
                onFocusOnNewCodeChanged(false)
                onFilterChanged()
            }
        }
    }

    private fun addComponents() {
        if (showScopeFilter) {
            add(scopeLabel)
            add(Box.createRigidArea(Dimension(4, 0)))
            add(scopeCombo)
            add(Box.createRigidArea(Dimension(4, 0)))
            add(JSeparator(JSeparator.VERTICAL).apply {
                maximumSize = Dimension(8, 24)
            })
            add(Box.createRigidArea(Dimension(4, 0)))
        }
        add(searchLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(searchField)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(severityLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(severityCombo)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        statusSpacingComponents.add(Box.createRigidArea(Dimension(4, 0)).also { add(it) })
        add(statusLabel)
        statusSpacingComponents.add(Box.createRigidArea(Dimension(4, 0)).also { add(it) })
        add(statusCombo)
        statusSpacingComponents.add(Box.createRigidArea(Dimension(4, 0)).also { add(it) })
        statusSeparator = JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        }
        add(statusSeparator)
        statusSpacingComponents.add(Box.createRigidArea(Dimension(4, 0)).also { add(it) })
        add(quickFixLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(quickFixCheckBox)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(sortLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(sortCombo)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(focusOnNewCodeLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(focusOnNewCodeCheckBox)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(cleanFiltersBtn)
    }

    fun setFocusOnNewCode(focusOnNewCode: Boolean) {
        focusOnNewCodeCheckBox.isSelected = focusOnNewCode
    }

    fun setStatusFilterVisible(visible: Boolean) {
        statusLabel.isVisible = visible
        statusCombo.isVisible = visible
        statusSeparator.isVisible = visible
        statusSpacingComponents.forEach { it.isVisible = visible }
    }

}
