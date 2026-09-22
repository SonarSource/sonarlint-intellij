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

import java.nio.file.Path
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

    @Test
    fun `provides normalized standalone MCP paths for all supported agents`() {
        val registry = AiAgentRegistry { false }
        val home = Path.of("/users/me").toAbsolutePath()

        assertThat(registry.standaloneMcpPath(AiAgentId.GITHUB_COPILOT, home, false, null))
            .isEqualTo(home.resolve(".config/github-copilot/intellij/mcp.json"))
        assertThat(registry.standaloneMcpPath(AiAgentId.GITHUB_COPILOT, home, true, Path.of("/local/appdata")))
            .isEqualTo(Path.of("/local/appdata/github-copilot/intellij/mcp.json"))
        assertThat(registry.standaloneMcpPath(AiAgentId.CURSOR, home, false, null))
            .isEqualTo(home.resolve(".cursor/mcp.json"))
        assertThat(registry.standaloneMcpPath(AiAgentId.CLAUDE_CODE, home, false, null))
            .isEqualTo(home.resolve(".claude.json"))
        assertThat(registry.standaloneMcpPath(AiAgentId.KIRO, home, false, null)).isNull()
    }
}
