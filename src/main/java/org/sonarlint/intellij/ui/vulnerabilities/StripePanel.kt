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
package org.sonarlint.intellij.ui.vulnerabilities

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class StripePanel(text: String, icon: Icon) : JPanel(FlowLayout(FlowLayout.CENTER)) {
    init {
        border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        add(JLabel(text, icon, SwingConstants.LEADING))
    }

    fun addAction(linkText: String, action: AnAction) {
        add(AnActionLink(linkText, action))
    }
}
