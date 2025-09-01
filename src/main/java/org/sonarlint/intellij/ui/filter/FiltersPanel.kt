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

import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JSeparator
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import org.sonarlint.intellij.common.util.SonarLintUtils.getService

enum class ScopeMode(val displayName: String, val tooltip: String) {
    CURRENT_FILE("Current File", "Show issues/hotspots/taints for the current file only"),
    OPEN_FILES("All Files", "Show all cached issues/hotspots/taints from analyzed files + project-wide SCA")
}

/**
 * Uses factory methods for component creation and separates concerns.
 */
class FiltersPanel(
    private val onFilterChanged: () -> Unit,
    private val onSortingChanged: (SortMode) -> Unit,
    private val onFocusOnNewCodeChanged: (Boolean) -> Unit,
    private val onScopeModeChanged: () -> Unit = {},
    private val showScopeFilter: Boolean = true
) : JBPanel<FiltersPanel>() {

    // Component references - created via factory
    val scopeLabel = FilterComponentFactory.createLabel("Scope:")
    val scopeCombo = FilterComponentFactory.createScopeCombo()
    val searchLabel = FilterComponentFactory.createLabel("Search:")
    val searchField = FilterComponentFactory.createSearchField()
    val severityLabel = FilterComponentFactory.createLabel("Severity:")
    val severityCombo = FilterComponentFactory.createSeverityCombo()
    val statusLabel = FilterComponentFactory.createLabel("Status:")
    val statusCombo = FilterComponentFactory.createStatusCombo()
    val quickFixLabel = FilterComponentFactory.createLabel("Fix suggestion:")
    val quickFixCheckBox = FilterComponentFactory.createQuickFixCheckBox()
    val sortLabel = FilterComponentFactory.createLabel("Sort by:")
    val sortCombo = FilterComponentFactory.createSortCombo()
    val focusOnNewCodeLabel = FilterComponentFactory.createLabel("New code:")
    val focusOnNewCodeCheckBox = FilterComponentFactory.createFocusOnNewCodeCheckBox()
    val cleanFiltersBtn = FilterComponentFactory.createDefaultButton()

    // Status filter visibility management
    private lateinit var statusSeparator: JSeparator
    private val statusSpacingComponents = mutableListOf<Component>()

    // Filter state
    var scopeMode = ScopeMode.CURRENT_FILE
    var filterText = ""
    var filterSeverity: SeverityImpactFilter = SeverityImpactFilter.Severity(SeverityFilter.NO_FILTER)
    var filterStatus = StatusFilter.OPEN
    private var sortMode = getService(FilterSettingsService::class.java).getDefaultSortMode()
    private var isMqrMode = true

    init {
        layout = FlowWrapLayout(hgap = 4, vgap = 4)
        border = JBUI.Borders.empty(4)
        isVisible = false

        setupComponents()
        addComponents()
    }

    private fun setupComponents() {
        setupScopeComponents()
        setupSearchComponents()
        setupSeverityComponents()
        setupStatusComponents()
        setupQuickFixComponents()
        setupSortComponents()
        setupFocusOnNewCodeComponents()
        setupDefaultButton()
    }

    private fun setupScopeComponents() {
        scopeCombo.apply {
            selectedItem = ScopeMode.CURRENT_FILE
            addActionListener {
                scopeMode = selectedItem as ScopeMode
                onScopeModeChanged()
                onFilterChanged()
            }
        }
    }

    private fun setupSearchComponents() {
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateSearchFilter()
            override fun removeUpdate(e: DocumentEvent) = updateSearchFilter()
            override fun changedUpdate(e: DocumentEvent) = updateSearchFilter()
            
            private fun updateSearchFilter() {
                filterText = searchField.text
                onFilterChanged()
            }
        })
    }

    private fun setupSeverityComponents() {
        severityCombo.apply {
            addActionListener {
                filterSeverity = when (selectedItem) {
                    is SeverityFilter -> SeverityImpactFilter.Severity(selectedItem as SeverityFilter)
                    is MqrImpactFilter -> SeverityImpactFilter.MqrImpact(selectedItem as MqrImpactFilter)
                    else -> SeverityImpactFilter.Severity(SeverityFilter.NO_FILTER)
                }
                onFilterChanged()
            }
        }
        // Initialize with default MQR mode
        updateSeverityComboModel()
    }

    private fun setupStatusComponents() {
        statusCombo.apply {
            addActionListener {
                filterStatus = selectedItem as StatusFilter
                onFilterChanged()
            }
        }
    }

    private fun setupQuickFixComponents() {
        quickFixCheckBox.addActionListener {
            onFilterChanged()
        }
    }

    private fun setupSortComponents() {
        sortCombo.apply {
            selectedItem = sortMode
            addActionListener {
                sortMode = selectedItem as SortMode
                onSortingChanged(sortMode)
            }
        }
    }

    private fun setupFocusOnNewCodeComponents() {
        focusOnNewCodeCheckBox.addActionListener {
            onFocusOnNewCodeChanged(focusOnNewCodeCheckBox.isSelected)
        }
    }

    private fun setupDefaultButton() {
        cleanFiltersBtn.addActionListener {
            resetFilters()
        }
    }

    private fun addComponents() {
        if (showScopeFilter) {
            add(FilterComponentFactory.createGroup(scopeLabel, scopeCombo))
            add(FilterComponentFactory.createSeparator())
        }
        
        add(FilterComponentFactory.createGroup(searchLabel, searchField))
        add(FilterComponentFactory.createSeparator())
        
        add(FilterComponentFactory.createGroup(severityLabel, severityCombo))
        add(FilterComponentFactory.createSeparator())
        
        // Status filter group (conditionally visible)
        statusSpacingComponents.clear()
        val statusGroup = FilterComponentFactory.createGroup(statusLabel, statusCombo)
        statusSpacingComponents.add(statusGroup)
        add(statusGroup)
        
        statusSeparator = FilterComponentFactory.createSeparator()
        statusSpacingComponents.add(statusSeparator)
        add(statusSeparator)
        
        add(FilterComponentFactory.createGroup(quickFixLabel, quickFixCheckBox))
        add(FilterComponentFactory.createSeparator())
        
        add(FilterComponentFactory.createGroup(sortLabel, sortCombo))
        add(FilterComponentFactory.createSeparator())
        
        add(FilterComponentFactory.createGroup(focusOnNewCodeLabel, focusOnNewCodeCheckBox))
        add(FilterComponentFactory.createSeparator())
        
        add(cleanFiltersBtn)
    }

    private fun resetFilters() {
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

    override fun doLayout() {
        super.doLayout()
        parent?.revalidate()
    }
    
    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (visible) {
            revalidate()
        }
    }

    fun setFocusOnNewCode(focusOnNewCode: Boolean) {
        focusOnNewCodeCheckBox.isSelected = focusOnNewCode
    }

    private fun updateSeverityComboModel() {
        val newOptions = if (isMqrMode) MqrImpactFilter.values() else SeverityFilter.values()
        severityCombo.setModel(DefaultComboBoxModel(newOptions))
    }

    fun setStatusFilterVisible(visible: Boolean) {
        statusLabel.isVisible = visible
        statusCombo.isVisible = visible
        statusSeparator.isVisible = visible
        statusSpacingComponents.forEach { it.isVisible = visible }
        
        revalidate()
        repaint()
    }

}
