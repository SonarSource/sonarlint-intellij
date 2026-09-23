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

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import java.nio.file.Path

fun interface IdePluginDetector {
    fun isInstalledAndEnabled(pluginId: String): Boolean
}

class IntellijIdePluginDetector : IdePluginDetector {
    override fun isInstalledAndEnabled(pluginId: String): Boolean =
        PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.isEnabled == true
}

class AiAgentRegistry(private val pluginDetector: IdePluginDetector = IntellijIdePluginDetector()) {
    fun detectedIdeAgents(): List<AiAgentId> = buildList {
        if (pluginDetector.isInstalledAndEnabled(GITHUB_COPILOT_PLUGIN_ID)) {
            add(AiAgentId.GITHUB_COPILOT)
        }
    }

    fun displayName(agent: AiAgentId): String = DISPLAY_NAMES.getValue(agent)

    fun standaloneMcpPath(agent: AiAgentId): Path? {
        val userHome = Path.of(SystemProperties.getUserHome()).toAbsolutePath().normalize()
        val localAppData = System.getenv("LOCALAPPDATA")?.let(Path::of)
        return standaloneMcpPath(agent, userHome, SystemInfo.isWindows, localAppData)
    }

    internal fun standaloneMcpPath(
        agent: AiAgentId,
        userHome: Path,
        windows: Boolean,
        localAppData: Path?
    ): Path? {
        return when (agent) {
            AiAgentId.GITHUB_COPILOT -> if (windows) {
                (localAppData ?: userHome.resolve("AppData/Local"))
                    .resolve("github-copilot/intellij/mcp.json").toAbsolutePath().normalize()
            } else {
                userHome.resolve(".config/github-copilot/intellij/mcp.json")
            }
            AiAgentId.CURSOR -> userHome.resolve(".cursor/mcp.json")
            AiAgentId.CLAUDE_CODE -> userHome.resolve(".claude.json")
            else -> null
        }
    }

    companion object {
        const val GITHUB_COPILOT_PLUGIN_ID = "com.github.copilot"

        private val DISPLAY_NAMES = mapOf(
            AiAgentId.CURSOR to "Cursor",
            AiAgentId.GITHUB_COPILOT to "GitHub Copilot",
            AiAgentId.KIRO to "Kiro",
            AiAgentId.WINDSURF to "Windsurf",
            AiAgentId.CLAUDE_CODE to "Claude Code",
            AiAgentId.CODEX to "Codex",
            AiAgentId.GITHUB_COPILOT_CLI to "GitHub Copilot CLI",
            AiAgentId.ANTIGRAVITY to "Antigravity"
        )
    }
}
