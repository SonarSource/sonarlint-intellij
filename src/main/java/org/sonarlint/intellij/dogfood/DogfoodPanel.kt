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
package org.sonarlint.intellij.dogfood

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBLabel
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JTextField
import org.jdesktop.swingx.HorizontalLayout

class DogfoodPanel : JPanel() {

    private val usernameField = JTextField()
    private val passwordField = JTextField()

    init {
        layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 15, true, false)

        val usernamePanel = JPanel(HorizontalLayout(5))
        val usernameLabel = JBLabel("Username")
        usernamePanel.add(usernameLabel)
        usernamePanel.add(usernameField)
        usernameField.preferredSize = Dimension(400, usernameField.preferredSize.height)

        val passwordPanel = JPanel(HorizontalLayout(5))
        val passwordLabel = JBLabel("Password")
        passwordPanel.add(passwordLabel)
        passwordPanel.add(passwordField)
        passwordField.preferredSize = Dimension(400, passwordField.preferredSize.height)

        add(usernamePanel)
        add(passwordPanel)
    }

    fun getUsername(): String = usernameField.text

    fun getPassword(): String = passwordField.text

}