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
package org.sonarlint.intellij.ui.factory

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.JBSplitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.HorizontalLayout
import java.awt.event.ActionEvent
import javax.swing.JComponent
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID

private const val LAYOUT_GAP = 5

class PanelFactory {

    companion object {
        @JvmOverloads
        @JvmStatic
        fun centeredLabel(textLabel: String, actionText: String? = null, action: AnAction? = null): JBPanelWithEmptyText {
            val labelPanel = JBPanelWithEmptyText(HorizontalLayout(LAYOUT_GAP))
            val text = labelPanel.emptyText
            text.text = textLabel
            if (action != null && actionText != null) {
                text.appendLine(
                    actionText, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
                ) { _: ActionEvent ->
                    ActionUtil.invokeAction(
                        action,
                        labelPanel,
                        TOOL_WINDOW_ID,
                        null,
                        null
                    )
                }
            }
            return labelPanel
        }

        @JvmStatic
        fun createSplitter(
            project: Project,
            parentComponent: JComponent,
            parentDisposable: Disposable,
            c1: JComponent,
            c2: JComponent,
            proportionProperty: String,
            defaultSplit: Float,
        ): JBSplitter {
            val splitter = OnePixelSplitter(splitVertically(project), proportionProperty, defaultSplit)
            splitter.setFirstComponent(c1)
            splitter.setSecondComponent(c2)
            splitter.setHonorComponentsMinimumSize(true)

            val listener: ToolWindowManagerListener? = object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    splitter.setOrientation(splitVertically(project))
                    parentComponent.revalidate()
                    parentComponent.repaint()
                }
            }
            project.messageBus.connect(parentDisposable)
                .subscribe(ToolWindowManagerListener.TOPIC!!, listener!!)
            Disposer.register(parentDisposable) {
                parentComponent.remove(splitter)
                splitter.dispose()
            }

            return splitter
        }

        private fun splitVertically(project: Project): Boolean {
            val toolWindow: ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
            var splitVertically = false
            if (toolWindow != null) {
                val anchor = toolWindow.anchor
                splitVertically = anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT
            }
            return splitVertically
        }
    }
}
