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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "SonarLintAiIntegrationOwnership", storages = [Storage("sonarlint-ai-integrations.xml")])
class McpConfigurationOwnership : PersistentStateComponent<McpConfigurationOwnership.State> {
    data class State(
        var connectionByAgent: MutableMap<String, String> = mutableMapOf(),
        var fingerprintByAgent: MutableMap<String, String> = mutableMapOf()
    )

    private var state = State()

    override fun getState(): State = synchronized(this) {
        State(state.connectionByAgent.toMutableMap(), state.fingerprintByAgent.toMutableMap())
    }

    override fun loadState(state: State) {
        synchronized(this) {
            this.state = State(state.connectionByAgent.toMutableMap(), state.fingerprintByAgent.toMutableMap())
        }
    }

    fun connectionId(agent: AiAgentId): String? = synchronized(this) {
        state.connectionByAgent[agent.name]
    }

    fun record(agent: AiAgentId): ManagedMcpOwnership? = synchronized(this) {
        state.connectionByAgent[agent.name]?.let { connectionId ->
            ManagedMcpOwnership(connectionId, state.fingerprintByAgent[agent.name])
        }
    }

    fun remember(agent: AiAgentId, connectionId: String, fingerprint: String) {
        synchronized(this) {
            state.connectionByAgent[agent.name] = connectionId
            state.fingerprintByAgent[agent.name] = fingerprint
        }
    }

    fun clear(agent: AiAgentId) {
        synchronized(this) {
            state.connectionByAgent.remove(agent.name)
            state.fingerprintByAgent.remove(agent.name)
        }
    }

    fun clearIfMatches(agent: AiAgentId, expected: ManagedMcpOwnership) {
        synchronized(this) {
            if (record(agent) == expected) {
                state.connectionByAgent.remove(agent.name)
                state.fingerprintByAgent.remove(agent.name)
            }
        }
    }

    fun all(): Map<AiAgentId, ManagedMcpOwnership> = synchronized(this) {
        state.connectionByAgent.mapNotNull { (agent, connectionId) ->
            runCatching { AiAgentId.valueOf(agent) }.getOrNull()?.let {
                it to ManagedMcpOwnership(connectionId, state.fingerprintByAgent[agent])
            }
        }.toMap()
    }
}

data class ManagedMcpOwnership(val connectionId: String, val fingerprint: String?)
