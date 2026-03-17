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
package org.sonarlint.intellij.config.project

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

private val COLOR_BLUE = JBColor(Color(30, 100, 200), Color(100, 160, 255))
private val COLOR_DIMMED = UIUtil.getContextHelpForeground()
private val CELL_PADDING = JBUI.Borders.empty(0, 8)

class VersionCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
        label.horizontalAlignment = LEFT
        label.toolTipText = null
        label.border = CELL_PADDING

        if (value is SupportedLanguageRow) {
            val version = value.version
            if (version == null) {
                label.text = "–"
                if (!isSelected) {
                    label.foreground = COLOR_DIMMED
                }
            } else if (!isSelected && value.isVersionOverriddenByServer) {
                label.text = version
                label.foreground = COLOR_BLUE
                label.toolTipText = "Overriding local version ${value.localVersion}"
            } else {
                label.text = version
                if (!isSelected) {
                    label.foreground = UIUtil.getLabelForeground()
                }
            }
        }

        return label
    }
}
