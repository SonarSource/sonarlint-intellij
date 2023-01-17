/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.core.server.events

import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.core.EngineManager
import org.sonarlint.intellij.messages.GlobalConfigurationListener

class SubscribeOnConnectionEdit : GlobalConfigurationListener.Adapter() {

    override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
        val previousServerConnectionsByName = previousSettings.serverConnections.associateBy { it.name }
        newSettings.serverConnections.forEach { serverConnection ->
            if (previousServerConnectionsByName.containsKey(serverConnection.name)
                && previousServerConnectionsByName[serverConnection.name] != serverConnection
            ) {
                // connection has been edited, re-subscribe
                getService(ServerEventsService::class.java).autoSubscribe(
                    getService(EngineManager::class.java).getConnectedEngineIfStarted(serverConnection.name),
                    serverConnection
                )
            }
        }
    }
}
