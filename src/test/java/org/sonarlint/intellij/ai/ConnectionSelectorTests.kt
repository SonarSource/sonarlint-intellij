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
package org.sonarlint.intellij.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.Settings.getSettingsFor

class ConnectionSelectorTests : AbstractSonarLintLightTests() {
    private val first = IntegrationConnection("first", "https://first", null)
    private val second = IntegrationConnection("second", "https://second", "org")

    @Test
    fun `prefers current project connection`() {
        getSettingsFor(project).connectionName = "second"
        val selection = ConnectionSelector { _, _ -> error("chooser should not open") }
            .select(project, listOf(first, second), "first")

        assertThat(selection).isEqualTo(ConnectionSelection.Selected("second"))
    }

    @Test
    fun `uses recommended then sole eligible connection`() {
        val selector = ConnectionSelector { _, _ -> error("chooser should not open") }

        assertThat(selector.select(project, listOf(first, second), "second"))
            .isEqualTo(ConnectionSelection.Selected("second"))
        assertThat(selector.select(project, listOf(first), null))
            .isEqualTo(ConnectionSelection.Selected("first"))
    }

    @Test
    fun `uses chooser for several connections and supports cancellation`() {
        assertThat(ConnectionSelector { _, _ -> "second" }.select(project, listOf(first, second), null))
            .isEqualTo(ConnectionSelection.Selected("second"))
        assertThat(ConnectionSelector { _, _ -> null }.select(project, listOf(first, second), null))
            .isEqualTo(ConnectionSelection.Cancelled)
    }

    @Test
    fun `reports missing connections without opening the chooser`() {
        assertThat(ConnectionSelector { _, _ -> error("chooser should not open") }.select(project, emptyList(), null))
            .isEqualTo(ConnectionSelection.Missing)
    }
}
