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
package org.sonarlint.intellij.util

import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel

open class RoundedPanelWithBackgroundColor(
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    private val cornerAngle: Float = 20f
) : JPanel() {

    init {
        isOpaque = false
        cursor = Cursor.getDefaultCursor()
        super.updateUI()
        updateBackgroundColor(backgroundColor)
        updateBorderColor(borderColor)
    }

    fun updateBackgroundColor(newBackgroundColor: Color?) {
        this.background = newBackgroundColor
    }

    fun updateBorderColor(newBorderColor: Color?) {
        newBorderColor?.let {
            val customBorder = IdeBorderFactory.createRoundedBorder(cornerAngle.toInt(), 1)
            customBorder.setColor(it)
            border = customBorder
        }
    }

    override fun paintComponent(g: Graphics) {
        GraphicsUtil.setupRoundedBorderAntialiasing(g)
        val g2 = g as Graphics2D
        val rect = Rectangle(size)
        JBInsets.removeFrom(rect, insets)
        val rectangle2d = RoundRectangle2D.Float(
            rect.x.toFloat() + 0.5f, rect.y.toFloat() + 0.5f,
            rect.width.toFloat() - 1f, rect.height.toFloat() - 1f,
            cornerAngle, cornerAngle
        )
        val fillColor = background ?: UIUtil.getPanelBackground()
        g2.color = fillColor
        g2.fill(rectangle2d)
    }

}
