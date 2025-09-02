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

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/**
 * A layout manager that flows components horizontally and wraps to new lines when needed.
 * Maintains proper vertical alignment and spacing between components.
 */
class FlowWrapLayout(
    private val hgap: Int = 2,
    private val vgap: Int = 2
) : LayoutManager {
    
    override fun addLayoutComponent(name: String?, comp: Component?) {
        // Nothing to do
    }

    override fun removeLayoutComponent(comp: Component?) {
        // Nothing to do
    }
    
    override fun preferredLayoutSize(parent: Container): Dimension {
        return layoutSize(parent, true)
    }
    
    override fun minimumLayoutSize(parent: Container): Dimension {
        return layoutSize(parent, false)
    }
    
    override fun layoutContainer(parent: Container) {
        val insets = parent.insets
        val maxWidth = parent.width - (insets.left + insets.right)
        val componentCount = parent.componentCount
        
        if (componentCount == 0) return
        
        var x = insets.left
        var y = insets.top + vgap
        var rowHeight = 0
        var lineStart = 0
        
        // Layout components
        for (i in 0 until componentCount) {
            val component = parent.getComponent(i)
            if (!component.isVisible) continue
            
            val componentSize = component.preferredSize
            
            // Check if we need to wrap to next line
            if (x + componentSize.width > maxWidth && x > insets.left) {
                // Align previous row vertically
                alignRow(parent, lineStart, i - 1, y, rowHeight)
                
                // Start new row
                x = insets.left
                y += rowHeight + vgap
                rowHeight = 0
                lineStart = i
            }
            
            // Position component (will be adjusted in alignRow)
            component.setBounds(x, y, componentSize.width, componentSize.height)
            
            // Update position for next component
            x += componentSize.width + hgap
            rowHeight = maxOf(rowHeight, componentSize.height)
        }
        
        // Align the last row
        if (lineStart < componentCount) {
            alignRow(parent, lineStart, componentCount - 1, y, rowHeight)
        }
    }
    
    private fun alignRow(parent: Container, start: Int, end: Int, rowY: Int, rowHeight: Int) {
        // Center all components in the row vertically
        for (i in start..end) {
            val component = parent.getComponent(i)
            if (!component.isVisible) continue
            
            val bounds = component.bounds
            val yOffset = (rowHeight - bounds.height) / 2
            component.setBounds(bounds.x, rowY + yOffset, bounds.width, bounds.height)
        }
    }
    
    private fun layoutSize(parent: Container, preferred: Boolean): Dimension {
        val insets = parent.insets
        val componentCount = parent.componentCount
        val maxWidth = parent.width - (insets.left + insets.right)
        
        if (componentCount == 0) {
            return Dimension(insets.left + insets.right, insets.top + insets.bottom)
        }
        
        var width = 0
        var totalHeight = vgap
        var rowWidth = 0
        var rowHeight = 0
        
        for (i in 0 until componentCount) {
            val component = parent.getComponent(i)
            if (!component.isVisible) continue
            
            val componentSize = if (preferred) component.preferredSize else component.minimumSize
            
            // Check if we need to wrap to next line (only if parent has a width set)
            if (maxWidth > 0 && rowWidth + componentSize.width > maxWidth && rowWidth > 0) {
                // Finish current row
                width = maxOf(width, rowWidth)
                totalHeight += rowHeight + vgap
                
                // Start new row
                rowWidth = componentSize.width
                rowHeight = componentSize.height
            } else {
                // Add to current row
                if (rowWidth > 0) {
                    rowWidth += hgap
                }
                rowWidth += componentSize.width
                rowHeight = maxOf(rowHeight, componentSize.height)
            }
        }
        
        // Add the last row
        width = maxOf(width, rowWidth)
        totalHeight += rowHeight + vgap
        
        return Dimension(
            width + insets.left + insets.right,
            totalHeight + insets.top + insets.bottom
        )
    }

}
