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
package org.sonarlint.intellij.ui.review

import com.intellij.openapi.ui.VerticalFlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.ButtonGroup
import javax.swing.JPanel
import kotlin.properties.Delegates
import org.sonarlint.intellij.ui.options.OptionPanel
import org.sonarlint.intellij.ui.options.addComponents
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus

class ReviewSecurityHotspotPanel(
    allowedStatuses: List<HotspotStatus>,
    private val defaultStatus: HotspotStatus,
    val callbackForButton: (Boolean) -> Unit,
) : JPanel(),
    ActionListener {

    var selectedStatus: HotspotStatus by Delegates.observable(HotspotStatus.TO_REVIEW) { _, _, newValue -> callbackForButton(newValue != defaultStatus) }

    init {
        layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 15, true, false)
        display(allowedStatuses)
    }

    fun display(allowedStatuses: List<HotspotStatus>) {
        val buttonGroup = ButtonGroup()
        allowedStatuses.forEach { status ->
            val richStatus = org.sonarsource.sonarlint.core.client.utils.HotspotStatus.fromDto(status)
            val reviewOptionPanel = OptionPanel(richStatus.name, richStatus.title, richStatus.description)
            reviewOptionPanel.setSelected(defaultStatus == status)
            addComponents(buttonGroup, reviewOptionPanel)
            reviewOptionPanel.statusRadioButton.addActionListener(this)
            add(reviewOptionPanel)
        }
    }

    override fun actionPerformed(e: ActionEvent?) {
        e ?: return
        selectedStatus = HotspotStatus.valueOf(e.actionCommand)
    }
}
