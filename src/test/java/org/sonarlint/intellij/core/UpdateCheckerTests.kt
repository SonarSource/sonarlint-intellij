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
package org.sonarlint.intellij.core

import com.intellij.openapi.progress.DumbProgressIndicator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.fixtures.newSonarQubeConnection
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine

internal class UpdateCheckerTests : AbstractSonarLintLightTests() {
    private var connectedModeStorageSynchronizer: ConnectedModeStorageSynchronizer? = null
    private var server: ServerConnection? = null
    private val bindingManager: ProjectBindingManager = Mockito.mock(ProjectBindingManager::class.java)
    private val engine: ConnectedSonarLintEngine = Mockito.mock(ConnectedSonarLintEngine::class.java)

    @BeforeEach
    @Throws(InvalidBindingException::class)
    fun before() {
        replaceProjectService(ProjectBindingManager::class.java, bindingManager)

        projectSettings.projectKey = "key"
        projectSettings.connectionName = "serverId"
        server = createServer()
        Mockito.`when`(bindingManager.serverConnection).thenReturn(server)
        Mockito.`when`(bindingManager.connectedEngine).thenReturn(engine)

        connectedModeStorageSynchronizer = ConnectedModeStorageSynchronizer(project)
    }

    @Test
    @Throws(InvalidBindingException::class)
    fun do_nothing_if_no_engine() {
        Mockito.`when`(bindingManager.connectedEngine).thenThrow(IllegalStateException())
        connectedModeStorageSynchronizer!!.sync(DumbProgressIndicator.INSTANCE)

        Mockito.verifyNoInteractions(engine)
    }

    private fun createServer(): ServerConnection {
        return newSonarQubeConnection("server1", "http://localhost:9000")
    }
}
