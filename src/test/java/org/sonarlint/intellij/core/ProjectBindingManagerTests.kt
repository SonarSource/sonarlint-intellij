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

import com.intellij.openapi.progress.ProgressManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.fixtures.newSonarQubeConnection
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine

internal class ProjectBindingManagerTests : AbstractSonarLintLightTests() {
    private lateinit var projectBindingManager: ProjectBindingManager

    private val standaloneEngine: StandaloneSonarLintEngine = Mockito.mock(StandaloneSonarLintEngine::class.java)
    private val connectedEngine: ConnectedSonarLintEngine = Mockito.mock(ConnectedSonarLintEngine::class.java)

    @BeforeEach
    fun before() {
        val console = Mockito.mock(SonarLintConsole::class.java)
        val notifications = Mockito.mock(SonarLintProjectNotifications::class.java)
        replaceProjectService(SonarLintConsole::class.java, console)
        replaceProjectService(SonarLintProjectNotifications::class.java, notifications)
        getEngineManager().stopAllEngines(false)

        projectBindingManager = ProjectBindingManager(project, Mockito.mock(ProgressManager::class.java))
    }

    @Test
    @Throws(InvalidBindingException::class)
    fun should_create_facade_standalone() {
        Assertions.assertThat(projectBindingManager.getFacade(module)).isInstanceOf(StandaloneSonarLintFacade::class.java)
    }

    @Test
    @Throws(InvalidBindingException::class)
    fun should_get_connected_engine() {
        connectProjectTo(newSonarQubeConnection("server1"), "project1")
        getEngineManager().registerEngine(connectedEngine, "server1")

        val engine = projectBindingManager.connectedEngine

        Assertions.assertThat(engine).isEqualTo(connectedEngine)
    }

    @Test
    fun fail_get_connected_engine_if_not_connected() {
        val throwable = Assertions.catchThrowable { projectBindingManager.connectedEngine }

        Assertions.assertThat(throwable).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    @Throws(InvalidBindingException::class)
    fun should_create_facade_connected() {
        connectProjectTo(newSonarQubeConnection("server1"), "project1")
        getEngineManager().registerEngine(connectedEngine, "server1")

        val facade = projectBindingManager.getFacade(module)

        Assertions.assertThat(facade).isInstanceOf(ConnectedSonarLintFacade::class.java)
    }

    @Test
    @Throws(InvalidBindingException::class)
    fun should_find_sq_server() {
        projectSettings.isBindingEnabled = true
        projectSettings.projectKey = "project1"
        projectSettings.connectionName = "server1"

        val server = newSonarQubeConnection("server1")
        setServerConnections(listOf(server))
        Assertions.assertThat(projectBindingManager.serverConnection).isEqualTo(server)
    }

    @Test
    fun fail_if_cant_find_server() {
        projectSettings.isBindingEnabled = true
        projectSettings.projectKey = "project1"
        projectSettings.connectionName = "server1"
        val connection = newSonarQubeConnection("server2")
        setServerConnections(listOf(connection))

        val throwable = Assertions.catchThrowable { projectBindingManager.serverConnection }

        Assertions.assertThat(throwable).isInstanceOf(InvalidBindingException::class.java)
    }

    @Test
    fun fail_invalid_server_binding() {
        projectSettings.isBindingEnabled = true

        val throwable = Assertions.catchThrowable { projectBindingManager.getFacade(module) }

        Assertions.assertThat(throwable)
                .isInstanceOf(InvalidBindingException::class.java)
                .hasMessage("Project has an invalid binding")
    }

    @Test
    fun fail_invalid_module_binding() {
        projectSettings.isBindingEnabled = true
        projectSettings.connectionName = "server1"
        projectSettings.projectKey = null

        val throwable = Assertions.catchThrowable { projectBindingManager.getFacade(module) }

        Assertions.assertThat(throwable)
                .isInstanceOf(InvalidBindingException::class.java)
                .hasMessage("Project has an invalid binding")
    }

    @Test
    fun should_return_connected_engine_if_started() {
        projectSettings.isBindingEnabled = true
        projectSettings.connectionName = "server1"
        projectSettings.projectKey = "key"
        getEngineManager().registerEngine(connectedEngine, "server1")

        val engine = projectBindingManager.engineIfStarted

        Assertions.assertThat(engine).isEqualTo(connectedEngine)
    }

    @Test
    fun should_return_standalone_engine_if_started() {
        projectSettings.isBindingEnabled = false
        getEngineManager().registerEngine(standaloneEngine)

        val engine = projectBindingManager.engineIfStarted

        Assertions.assertThat(engine).isEqualTo(standaloneEngine)
    }

    @Test
    fun should_not_return_connected_engine_if_not_started() {
        projectSettings.isBindingEnabled = true
        projectSettings.connectionName = "server1"
        projectSettings.projectKey = null

        val engine = projectBindingManager.engineIfStarted

        Assertions.assertThat(engine).isNull()
    }

    @Test
    fun should_not_return_standalone_engine_if_not_started() {
        projectSettings.isBindingEnabled = false

        val engine = projectBindingManager.engineIfStarted

        Assertions.assertThat(engine).isNull()
    }

    @Test
    fun should_store_project_binding_in_settings() {
        val connection = newSonarQubeConnection("name")
        setServerConnections(listOf(connection))

        projectBindingManager.bindTo(connection, "projectKey", emptyMap())

        Assertions.assertThat(projectSettings.isBoundTo(connection)).isTrue()
        Assertions.assertThat(projectSettings.projectKey).isEqualTo("projectKey")
    }

    @Test
    fun should_store_project_and_module_bindings_in_settings() {
        val connection = newSonarQubeConnection("name")
        setServerConnections(listOf(connection))
        projectBindingManager.bindTo(connection, "projectKey", mapOf(module to "moduleProjectKey"))

        Assertions.assertThat(projectSettings.isBoundTo(connection)).isTrue()
        Assertions.assertThat(projectSettings.projectKey).isEqualTo("projectKey")
        Assertions.assertThat(moduleSettings.isProjectBindingOverridden).isTrue()
        Assertions.assertThat(moduleSettings.projectKey).isEqualTo("moduleProjectKey")
    }

    @Test
    fun should_clear_project_and_module_binding_settings_when_unbinding() {
        projectSettings.bindTo(newSonarQubeConnection("connection"), "projectKey")
        moduleSettings.projectKey = "moduleProjectKey"

        projectBindingManager.unbind()

        Assertions.assertThat(projectSettings.isBound).isFalse()
        Assertions.assertThat(moduleSettings.isProjectBindingOverridden).isFalse()
    }
}
