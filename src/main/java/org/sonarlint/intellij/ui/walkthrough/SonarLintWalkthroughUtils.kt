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
package org.sonarlint.intellij.ui.walkthrough

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JScrollPane

object SonarLintWalkthroughUtils {
    const val SONARQUBE_FOR_IDE: String = "SonarQube for IDE"
    const val PAGE_1: String = "Page 1"
    const val PAGE_2: String = "Page 2"
    const val PAGE_3: String = "Page 3"
    const val PAGE_4: String = "Page 4"
    const val FONT: String = "Arial"
    const val PREVIOUS: String = "Previous"
    const val WIDTH: Int = 300
    const val HEIGHT: Int = 200

    fun createCenterPanel(stepLabel: JBLabel, pageLabel: JBLabel, scrollPane: JScrollPane?, gbc: GridBagConstraints): JBPanel<JBPanel<*>> {
        val centerPanel = JBPanel<JBPanel<*>>(GridBagLayout())
        pageLabel.border = BorderFactory.createEmptyBorder(2, 8, 2, 0)
        stepLabel.border = BorderFactory.createEmptyBorder(2, 8, 2, 0)

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.NONE
        centerPanel.add(stepLabel, gbc)

        gbc.gridy = 1
        centerPanel.add(pageLabel, gbc)

        gbc.gridy = 2
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        centerPanel.add(scrollPane, gbc)

        return centerPanel
    }

    fun provideCommonButtonConstraints(gbc: GridBagConstraints) {
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.weighty = 0.0
    }
    
    fun addCenterPanel(
        stepLabel: JBLabel,
        pageLabel: JBLabel,
        scrollPane: JScrollPane,
        lefButtonPanel: JBPanel<JBPanel<*>>,
        rightButtonPanel: JBPanel<JBPanel<*>>,
        panel: JBPanel<JBPanel<*>>,
        imageLabel: JBLabel,
    ) {
        val gbc = GridBagConstraints()

        val centerPanel = createCenterPanel(stepLabel, pageLabel, scrollPane, gbc)

        gbc.anchor = GridBagConstraints.SOUTHWEST
        provideCommonButtonConstraints(gbc)
        centerPanel.add(lefButtonPanel, gbc)

        gbc.anchor = GridBagConstraints.SOUTHEAST
        centerPanel.add(rightButtonPanel, gbc)

        panel.add(imageLabel, BorderLayout.NORTH)
        panel.add(centerPanel, BorderLayout.CENTER)
    }
}
