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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.HorizontalLayout
import java.awt.event.ActionEvent
import org.sonarlint.intellij.ui.CurrentFilePanel

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
                ) { _: ActionEvent? ->
                    ActionUtil.invokeAction(
                        action,
                        labelPanel,
                        CurrentFilePanel.SONARLINT_TOOLWINDOW_ID,
                        null,
                        null
                    )
                }
            }
            return labelPanel
        }
    }
}
