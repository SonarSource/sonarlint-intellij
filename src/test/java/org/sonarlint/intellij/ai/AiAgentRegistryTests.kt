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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent

class AiAgentRegistryTests {
    @Test
    fun `detects each enabled plugin independently`() {
        supportedPlugins.forEach { (pluginId, agent) ->
            val registry = registry(mapOf(pluginId to pluginDescriptor(enabled = true)))

            assertThat(registry.detectedIdeAgents()).containsExactly(agent)
        }
    }

    @Test
    fun `does not detect disabled plugins`() {
        val registry = registry(supportedPlugins.mapValues { pluginDescriptor(enabled = false) })

        assertThat(registry.detectedIdeAgents()).isEmpty()
    }

    @Test
    fun `does not detect absent plugins`() {
        val registry = registry(emptyMap())

        assertThat(registry.detectedIdeAgents()).isEmpty()
    }

    @Test
    fun `detects enabled plugins in stable order`() {
        val registry = registry(supportedPlugins.entries.reversed().associate { (pluginId, _) ->
            pluginId to pluginDescriptor(enabled = true)
        })

        assertThat(registry.detectedIdeAgents()).containsExactly(
            AiAgent.GITHUB_COPILOT, AiAgent.JUNIE, AiAgent.JETBRAINS_AI_ASSISTANT
        )
    }

    @Test
    fun `detects AI Assistant when Junie and Copilot are disabled`() {
        val registry = registry(mapOf(
            "com.github.copilot" to pluginDescriptor(enabled = false),
            "org.jetbrains.junie" to pluginDescriptor(enabled = false),
            "com.intellij.ml.llm" to pluginDescriptor(enabled = true)
        ))

        assertThat(registry.detectedIdeAgents()).containsExactly(AiAgent.JETBRAINS_AI_ASSISTANT)
    }

    @Test
    fun `provides display names for every supported agent`() {
        val registry = AiAgentRegistry()
        val expectedNames = mapOf(
            AiAgent.CURSOR to "Cursor",
            AiAgent.GITHUB_COPILOT to "GitHub Copilot",
            AiAgent.JUNIE to "Junie",
            AiAgent.JETBRAINS_AI_ASSISTANT to "JetBrains AI Assistant",
            AiAgent.KIRO to "Kiro",
            AiAgent.WINDSURF to "Windsurf",
            AiAgent.CLAUDE_CODE to "Claude Code",
            AiAgent.CODEX to "Codex",
            AiAgent.GITHUB_COPILOT_CLI to "GitHub Copilot CLI",
            AiAgent.ANTIGRAVITY to "Antigravity"
        )

        assertThat(expectedNames.keys).containsExactlyInAnyOrderElementsOf(AiAgent.entries)
        expectedNames.forEach { (agent, name) ->
            assertThat(registry.displayName(agent)).isEqualTo(name)
        }
    }

    private val supportedPlugins = mapOf(
        "com.github.copilot" to AiAgent.GITHUB_COPILOT,
        "org.jetbrains.junie" to AiAgent.JUNIE,
        "com.intellij.ml.llm" to AiAgent.JETBRAINS_AI_ASSISTANT
    )

    private fun registry(plugins: Map<String, IdeaPluginDescriptor>) =
        AiAgentRegistry(IntellijIdePluginDetector { plugins[it.idString] })

    private fun pluginDescriptor(enabled: Boolean): IdeaPluginDescriptor =
        mock(IdeaPluginDescriptor::class.java).also { `when`(it.isEnabled).thenReturn(enabled) }
}
