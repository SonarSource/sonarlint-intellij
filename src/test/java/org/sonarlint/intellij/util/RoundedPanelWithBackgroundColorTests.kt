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
package org.sonarlint.intellij.util

import com.intellij.ui.JBColor
import java.awt.Cursor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoundedPanelWithBackgroundColorTests {

    @Test
    fun test_default_properties() {
        val panel = RoundedPanelWithBackgroundColor()

        assertThat(panel.isOpaque).isFalse()
        assertThat(panel.cursor).isEqualTo(Cursor.getDefaultCursor())
    }

    @Test
    fun should_set_background_color() {
        val color = JBColor.RED

        val panel = RoundedPanelWithBackgroundColor(backgroundColor = color)

        assertThat(panel.background).isEqualTo(color)
    }

}
