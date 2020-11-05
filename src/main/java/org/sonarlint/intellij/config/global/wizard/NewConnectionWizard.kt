/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.config.global.wizard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.SonarLintEngineManager
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.tasks.ServerUpdateTask
import org.sonarlint.intellij.util.SonarLintUtils
import java.util.Collections.emptyMap

open class NewConnectionWizard {

    fun open(serverUrl: String): Boolean {
        val globalSettings = Settings.getGlobalSettings()
        val serverToCreate = ServerConnection.newBuilder().setHostUrl(serverUrl).build()
        val wizard = ServerConnectionWizard(serverToCreate, globalSettings.serverNames)
        if (wizard.showAndGet()) {
            val created = wizard.server
            globalSettings.addServerConnection(created)
            val serverChangeListener = ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC)
            serverChangeListener.changed(globalSettings.serverConnections)
            val serverManager = SonarLintUtils.getService(SonarLintEngineManager::class.java)
            val task = ServerUpdateTask(serverManager.getConnectedEngine(created.name), created, emptyMap(), false)
            ProgressManager.getInstance().run(task.asBackground())
            return true
        }
        return false
    }
}
