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

import com.intellij.ide.plugins.IdeaPluginDescriptor
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent

class AiAgentRegistryTests {
    @Test
    fun `detects enabled GitHub Copilot plugin`() {
        val descriptor = pluginDescriptor(enabled = true)
        val registry = AiAgentRegistry(IntellijIdePluginDetector { pluginId ->
            assertThat(pluginId.idString).isEqualTo(AiAgentRegistry.GITHUB_COPILOT_PLUGIN_ID)
            descriptor
        })

        assertThat(registry.detectedIdeAgents()).containsExactly(AiAgent.GITHUB_COPILOT)
    }

    @Test
    fun `does not detect a disabled GitHub Copilot plugin`() {
        val registry = AiAgentRegistry(IntellijIdePluginDetector { pluginDescriptor(enabled = false) })

        assertThat(registry.detectedIdeAgents()).isEmpty()
    }

    @Test
    fun `does not detect an absent GitHub Copilot plugin`() {
        val registry = AiAgentRegistry(IntellijIdePluginDetector { null })

        assertThat(registry.detectedIdeAgents()).isEmpty()
    }

    @Test
    fun `provides normalized standalone MCP paths for all supported agents`() {
        val registry = AiAgentRegistry { false }
        val home = Path.of("/users/me").toAbsolutePath().normalize()
        val localAppData = Path.of("/local/appdata").toAbsolutePath().normalize()

        assertThat(registry.standaloneMcpPath(AiAgent.GITHUB_COPILOT, home, false, null))
            .isEqualTo(home.resolve(".config/github-copilot/intellij/mcp.json"))
        assertThat(registry.standaloneMcpPath(AiAgent.GITHUB_COPILOT, home, true, localAppData))
            .isEqualTo(localAppData.resolve("github-copilot/intellij/mcp.json").toAbsolutePath().normalize())
        assertThat(registry.standaloneMcpPath(AiAgent.CURSOR, home, false, null))
            .isEqualTo(home.resolve(".cursor/mcp.json"))
        assertThat(registry.standaloneMcpPath(AiAgent.CLAUDE_CODE, home, false, null))
            .isEqualTo(home.resolve(".claude.json"))
        assertThat(registry.standaloneMcpPath(AiAgent.KIRO, home, false, null)).isNull()
    }

    private fun pluginDescriptor(enabled: Boolean): IdeaPluginDescriptor =
        mock(IdeaPluginDescriptor::class.java).also { `when`(it.isEnabled).thenReturn(enabled) }
}
