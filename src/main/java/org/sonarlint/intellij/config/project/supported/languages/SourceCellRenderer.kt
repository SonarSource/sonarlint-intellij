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
package org.sonarlint.intellij.config.project.supported.languages

import com.intellij.ui.JBColor
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto

private val COLOR_BLUE = JBColor(Color(30, 100, 200), Color(100, 160, 255))
private val CELL_PADDING = JBUI.Borders.empty(0, 8)

class SourceCellRenderer : DefaultTableCellRenderer() {

    private val pluginVersion: String = SonarLintUtils.getService(SonarLintPlugin::class.java).version

    override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
        if (value is ArtifactSourceDto) {
            label.icon = when (value) {
                ArtifactSourceDto.SONARQUBE_SERVER -> SonarLintIcons.ICON_SONARQUBE_SERVER_16
                ArtifactSourceDto.SONARQUBE_CLOUD -> SonarLintIcons.ICON_SONARQUBE_CLOUD_16
                else -> EmptyIcon.ICON_16
            }
            label.text = when (value) {
                ArtifactSourceDto.SONARQUBE_SERVER,
                ArtifactSourceDto.SONARQUBE_CLOUD -> value.label
                else -> "${value.label} $pluginVersion"
            }
            if (!isSelected && (value == ArtifactSourceDto.SONARQUBE_SERVER || value == ArtifactSourceDto.SONARQUBE_CLOUD)) {
                label.foreground = COLOR_BLUE
            }
        }
        label.horizontalAlignment = LEFT
        label.border = CELL_PADDING
        return label
    }

}
