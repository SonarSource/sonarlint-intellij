/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.page

import javax.swing.JComponent
import javax.swing.KeyStroke
import org.sonarlint.intellij.ui.currentfile.SummaryButton

class SummaryFilterPage internal constructor(private val button: SummaryButton) {
    val accessibleContext get() = button.accessibleContext
    val isFocusable get() = button.isFocusable
    val isSelected get() = button.isSelected()
    var isEnabled: Boolean
        get() = button.isEnabled
        set(value) { button.isEnabled = value }

    fun resetSelection() = button.setSelected(false)

    fun pressSpace() {
        invokeKey("pressed SPACE")
        invokeKey("released SPACE")
    }

    private fun invokeKey(stroke: String) {
        val key = button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(stroke))
        checkNotNull(button.actionMap.get(key)).actionPerformed(null)
    }
}
