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

/**
 * Factory for creating filter UI components with consistent styling and behavior.
 */
object FilterComponentFactory {
    
    fun createLabel(text: String): JBLabel {
        return JBLabel(text).apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }
    }
    
    fun createScopeCombo(): ComboBox<ScopeMode> {
        return ComboBox<ScopeMode>().apply {
            toolTipText = "Filter scope: current file only or all files (Issues And Security Hotspots are displayed for opened files)"
            maximumSize = Dimension(120, 30)
            
            // Add all scope mode values
            ScopeMode.values().forEach { addItem(it) }
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
        }
    }
    
    fun createSearchField(): SearchTextField {
        return SearchTextField().apply {
            toolTipText = "Search by rule name, message or file"
            textEditor.columns = 12
            maximumSize = Dimension(160, 30)
            preferredSize = Dimension(120, 30)
        }
    }
    
    fun createSeverityCombo(): ComboBox<Any> {
        return ComboBox<Any>().apply {
            toolTipText = "Filter by severity"
            maximumSize = Dimension(90, 30)
            addItem(SeverityFilter.NO_FILTER)
            // Note: Specific severity values will be added by the panel based on MQR mode
        }
    }
    
    fun createStatusCombo(): ComboBox<StatusFilter> {
        return ComboBox<StatusFilter>().apply {
            toolTipText = "Filter by status"
            maximumSize = Dimension(90, 30)
            StatusFilter.values().forEach { addItem(it) }
            selectedItem = StatusFilter.OPEN
        }
    }
    
    fun createQuickFixCheckBox(): JBCheckBox {
        return JBCheckBox().apply {
            toolTipText = "Show only issues with quick fixes available"
        }
    }
    
    fun createSortCombo(): ComboBox<SortMode> {
        return ComboBox<SortMode>().apply {
            toolTipText = "Sort findings"
            maximumSize = Dimension(160, 30)
            SortMode.values().forEach { addItem(it) }
        }
    }
    
    fun createFocusOnNewCodeCheckBox(): JBCheckBox {
        return JBCheckBox().apply {
            toolTipText = "Focus on new code"
        }
    }
    
    fun createDefaultButton(): JButton {
        return JButton("Default").apply {
            toolTipText = "Reset filters to default values"
        }
    }
    
    fun createGroup(label: JBLabel, control: Component): JBPanel<*> {
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyRight(4)
            
            add(label)
            add(Box.createRigidArea(Dimension(4, 0)))
            add(control)

            alignmentY = Component.CENTER_ALIGNMENT
        }
    }
    
    fun createSeparator(): JSeparator {
        return JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
            preferredSize = Dimension(8, 24)
        }
    }

}
