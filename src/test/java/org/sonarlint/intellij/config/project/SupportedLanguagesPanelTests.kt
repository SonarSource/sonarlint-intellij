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
package org.sonarlint.intellij.config.project

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SupportedLanguagesPanelTests {

    @Test
    fun `should create panel with component`() {
        val panel = SupportedLanguagesPanel()

        assertThat(panel.component).isNotNull()
    }

    @Test
    fun `should load settings without error`() {
        val panel = SupportedLanguagesPanel()
        val settings = SonarLintProjectSettings()

        panel.load(settings)

        assertThat(panel.isModified(settings)).isFalse()
    }

    @Test
    fun `should save settings without error`() {
        val panel = SupportedLanguagesPanel()
        val settings = SonarLintProjectSettings()

        panel.save(settings)

        assertThat(panel.isModified(settings)).isFalse()
    }

}
