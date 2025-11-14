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
package org.sonarlint.intellij.ui.icons

import com.intellij.ui.scale.JBUIScale.isUsrHiDPI
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FindingIconBuilderTest {

    companion object {
        private val GAP = if (isUsrHiDPI) 8 else 4
        private val ICON_SIZE = EmptyIcon.ICON_16.iconWidth
    }

    @Test
    fun `should have two icons compound`() {
        val actual = FindingIconBuilder.forBaseIcon(EmptyIcon.ICON_16)
            .withDecoratingIcon(EmptyIcon.ICON_16)
            .withAiCodeFix(false)
            .build()

        assertThat(actual).isNotNull
        assertThat(actual).extracting("iconWidth", "iconHeight")
            .containsExactly(
                ICON_SIZE + GAP + ICON_SIZE,
                ICON_SIZE
            )
    }

    @Test
    fun `should handle null values`() {
        val actual = FindingIconBuilder.forBaseIcon(null)
            .withDecoratingIcon(null)
            .build()

        assertThat(actual).isNotNull
        assertThat(actual).extracting("iconWidth", "iconHeight")
            .containsExactly(
                ICON_SIZE + GAP + ICON_SIZE,
                ICON_SIZE
            )
    }

    @Test
    fun `should add ai code fix icon`() {
        val actual = FindingIconBuilder.forBaseIcon(EmptyIcon.ICON_16)
            .withDecoratingIcon(EmptyIcon.ICON_16)
            .withAiCodeFix(true)
            .build()

        assertThat(actual).isNotNull
        assertThat(actual).extracting("iconWidth", "iconHeight")
            .containsExactly(
                ICON_SIZE + GAP + ICON_SIZE + GAP + SonarLintIcons.SPARKLE_GUTTER_ICON.iconWidth,
                ICON_SIZE
            )
    }

    @Test
    fun `should build undecorated icon`() {
        val actual = FindingIconBuilder.forBaseIcon(EmptyIcon.ICON_16)
            .undecorated()
            .build()

        assertThat(actual).isNotNull
        assertThat(actual).extracting("iconWidth", "iconHeight")
            .containsExactly(
                ICON_SIZE,
                ICON_SIZE
            )
    }
}
