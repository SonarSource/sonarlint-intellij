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
package org.sonarlint.intellij.config.project.supported.languages

import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import org.sonarlint.intellij.ui.ruledescription.RuleLanguages
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto

private val CELL_PADDING = JBUI.Borders.empty(0, 8)

class LanguageCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int, ): Component {
        val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
        if (value is PluginStatusDto) {
            val fileType = value.language?.let { RuleLanguages.findFileTypeByRuleLanguage(it) }
            label.icon = if (fileType is UnknownFileType || fileType == null) EmptyIcon.ICON_16 else fileType.icon
            label.text = value.pluginName
        }
        label.border = CELL_PADDING
        return label
    }

}
