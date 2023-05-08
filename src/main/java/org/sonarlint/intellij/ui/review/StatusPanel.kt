/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.ui.review

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.JBUI
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory

class StatusPanel(
    status: HotspotStatus,
    isSelected: Boolean,
) : JBPanel<StatusPanel>(GridBagLayout()) {

    val statusRadioButton = JBRadioButton()

    init {
        statusRadioButton.isSelected = isSelected
        statusRadioButton.actionCommand = status.name

        val statusLabel = JBLabel(status.title)
        statusLabel.font = Font(statusLabel.font.name, Font.BOLD, statusLabel.font.size)
        val descriptionLabel = JBLabel(status.description)

        border = BorderFactory.createLineBorder(JBColor.BLACK, 1, true)

        add(
            statusRadioButton, GridBagConstraints(
                0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(10), 0, 0
            )
        )
        add(
            statusLabel, GridBagConstraints(
                1, 0, 1, 1, 0.5, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(10, 0, 10, 10), 0, 0
            )
        )
        add(
            descriptionLabel, GridBagConstraints(
                1, 1, 1, 1, 0.5, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(5, 0, 10, 10), 0, 0
            )
        )
    }

}
