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
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer
import org.sonarlint.intellij.config.project.supported.languages.StatusCellRenderer.GreenDotIcon
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto

private val COLOR_GREEN = JBColor(Color(34, 139, 34), Color(80, 200, 80))
private val COLOR_BLUE = JBColor(Color(30, 100, 200), Color(100, 160, 255))
private val COLOR_RED = JBColor(Color(180, 30, 30), Color(230, 80, 80))
private val CELL_PADDING = JBUI.Borders.empty(0, 8)
private val DOT_AREA_WIDTH = JBUI.scale(GreenDotIcon.SIZE + 4)

class StatusCellRenderer : TableCellRenderer {

    // Dot placeholder: fixed-width panel that shows the green dot for ACTIVE
    private val dotPlaceholder = object : JPanel() {
        var showDot = false
        init {
            isOpaque = false
            preferredSize = Dimension(DOT_AREA_WIDTH, 0)
        }
        override fun paintComponent(g: Graphics) {
            if (showDot) GreenDotIcon.paintIcon(
                this, g,
                (width - GreenDotIcon.SIZE) / 2,
                (height - GreenDotIcon.SIZE) / 2
            )
        }
    }

    private val textLabel = JLabel().apply {
        horizontalAlignment = SwingConstants.LEFT
    }

    private val cell = JPanel(BorderLayout()).apply {
        isOpaque = true
        add(dotPlaceholder, BorderLayout.WEST)
        add(textLabel, BorderLayout.CENTER)
    }

    override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val bg = if (isSelected) table.selectionBackground else table.background
        val fg = if (isSelected) table.selectionForeground else table.foreground

        cell.background = bg
        textLabel.background = bg
        cell.border = CELL_PADDING
        textLabel.toolTipText = null
        dotPlaceholder.showDot = false

        if (value is PluginStateDto) {
            textLabel.text = "lo"

            textLabel.foreground = if (isSelected) fg else when (value) {
                PluginStateDto.ACTIVE -> COLOR_GREEN
                PluginStateDto.SYNCED -> COLOR_BLUE
                PluginStateDto.FAILED -> COLOR_RED
                else -> fg
            }

            dotPlaceholder.showDot = value == PluginStateDto.ACTIVE
        }

        return cell
    }

    internal object GreenDotIcon : Icon {
        const val SIZE = 8

        override fun getIconWidth() = SIZE
        override fun getIconHeight() = SIZE

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = COLOR_GREEN
            g2.fillOval(x, y, SIZE, SIZE)
            g2.dispose()
        }
    }

}
