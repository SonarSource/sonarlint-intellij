/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.ui.grip

import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class AiLocationsItemRenderer : DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        val component = SimpleColoredComponent()

        if (value is InlaySnippetData) {
            val statusLabel = when (value.status) {
                AiFindingState.ACCEPTED -> JBLabel(AllIcons.RunConfigurations.ToolbarPassed).apply {
                    toolTipText = "Accepted"
                }

                AiFindingState.DECLINED -> JBLabel(AllIcons.Vcs.Remove).apply {
                    toolTipText = "Declined"
                }

                AiFindingState.PARTIAL -> JBLabel(AllIcons.General.InspectionsMixed).apply {
                    toolTipText = "Partially Accepted"
                }

                AiFindingState.LOADING -> JBLabel(AllIcons.Actions.BuildLoadChanges).apply {
                    toolTipText = "Loading"
                }

                AiFindingState.FAILED -> JBLabel(AllIcons.RunConfigurations.ToolbarError).apply {
                    toolTipText = "Failed"
                }

                else -> JBLabel()
            }

            if (isSelected) {
                panel.background = list?.selectionBackground
                panel.foreground = list?.selectionForeground
            } else {
                panel.background = list?.background
                panel.foreground = list?.foreground
            }

            panel.add(statusLabel, BorderLayout.WEST)

            // Add the SimpleColoredComponent to the CENTER of the panel
            panel.add(component, BorderLayout.CENTER)

            component.append("${value.inlayPanel.file.name} - Line ${value.inlayPanel.inlayLine}")
        }
        return panel
    }

}
