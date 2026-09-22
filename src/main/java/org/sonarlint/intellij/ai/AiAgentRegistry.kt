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
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import java.nio.file.Path
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent

fun interface IdePluginDetector {
    fun isInstalledAndEnabled(pluginId: String): Boolean
}

class IntellijIdePluginDetector(
    private val pluginLookup: (PluginId) -> IdeaPluginDescriptor? = { PluginManagerCore.getPlugin(it) }
) : IdePluginDetector {
    override fun isInstalledAndEnabled(pluginId: String): Boolean =
        pluginLookup(PluginId.getId(pluginId))?.isEnabled == true
}

class AiAgentRegistry(private val pluginDetector: IdePluginDetector = IntellijIdePluginDetector()) {
    fun detectedIdeAgents(): List<AiAgent> = buildList {
        if (pluginDetector.isInstalledAndEnabled(GITHUB_COPILOT_PLUGIN_ID)) {
            add(AiAgent.GITHUB_COPILOT)
        }
        if (pluginDetector.isInstalledAndEnabled(JUNIE_PLUGIN_ID)) {
            add(AiAgent.JUNIE)
        }
        if (pluginDetector.isInstalledAndEnabled(JETBRAINS_AI_ASSISTANT_PLUGIN_ID)) {
            add(AiAgent.JETBRAINS_AI_ASSISTANT)
        }
    }

    fun displayName(agent: AiAgent): String = DISPLAY_NAMES.getValue(agent)

    fun standaloneMcpPath(agent: AiAgent): Path? {
        val userHome = Path.of(SystemProperties.getUserHome()).toAbsolutePath().normalize()
        val localAppData = System.getenv("LOCALAPPDATA")?.let(Path::of)
        return standaloneMcpPath(agent, userHome, SystemInfo.isWindows, localAppData)
    }

    internal fun standaloneMcpPath(
        agent: AiAgent,
        userHome: Path,
        windows: Boolean,
        localAppData: Path?
    ): Path? {
        return when (agent) {
            AiAgent.GITHUB_COPILOT -> if (windows) {
                (localAppData ?: userHome.resolve("AppData/Local"))
                    .resolve("github-copilot/intellij/mcp.json").toAbsolutePath().normalize()
            } else {
                userHome.resolve(".config/github-copilot/intellij/mcp.json")
            }
            AiAgent.CURSOR -> userHome.resolve(".cursor/mcp.json")
            AiAgent.CLAUDE_CODE -> userHome.resolve(".claude.json")
            else -> null
        }
    }

    companion object {
        const val GITHUB_COPILOT_PLUGIN_ID = "com.github.copilot"
        const val JUNIE_PLUGIN_ID = "org.jetbrains.junie"
        const val JETBRAINS_AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

        private val DISPLAY_NAMES = mapOf(
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
    }
}
