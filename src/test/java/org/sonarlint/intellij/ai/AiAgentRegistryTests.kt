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

class AiAgentRegistryTests {
    @Test
    fun `detects enabled GitHub Copilot plugin`() {
        val registry = AiAgentRegistry { pluginId -> pluginId == AiAgentRegistry.GITHUB_COPILOT_PLUGIN_ID }

        assertThat(registry.detectedIdeAgents()).containsExactly(AiAgentId.GITHUB_COPILOT)
    }

    @Test
    fun `does not detect a disabled GitHub Copilot plugin`() {
        val registry = AiAgentRegistry { false }

        assertThat(registry.detectedIdeAgents()).isEmpty()
    }

    @Test
    fun `does not detect an absent GitHub Copilot plugin`() {
        val registry = AiAgentRegistry { false }

        assertThat(registry.detectedIdeAgents()).isEmpty()
    }
}
