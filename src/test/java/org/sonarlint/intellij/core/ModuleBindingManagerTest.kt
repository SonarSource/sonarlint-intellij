package org.sonarlint.intellij.core

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine

class ModuleBindingManagerTest : AbstractSonarLintLightTests() {

    private lateinit var moduleBindingManager: ModuleBindingManager

    @Before
    fun prepare() {
        val console = mock(SonarLintConsole::class.java)
        val notifications = mock(SonarLintProjectNotifications::class.java)
        replaceProjectService(SonarLintConsole::class.java, console)
        replaceProjectService(SonarLintProjectNotifications::class.java, notifications)

        `when`(engineManager.standaloneEngine).thenReturn(standaloneEngine)
        `when`(engineManager.getConnectedEngine(ArgumentMatchers.any(SonarLintProjectNotifications::class.java), ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(connectedEngine)
        moduleBindingManager = ModuleBindingManager(module) { engineManager }
    }

    @Test
    fun should_return_standalone_engine_if_not_bound_on_project_and_module_level() {
        `when`(engineManager.standaloneEngineIfStarted).thenReturn(standaloneEngine)

        assertThat(moduleBindingManager.engineIfStarted).isEqualTo(standaloneEngine)
    }

    @Test
    fun should_return_connected_engine_if_not_bound_on_module_level_but_has_default_binding() {
        `when`(engineManager.getConnectedEngineIfStarted(ArgumentMatchers.anyString())).thenReturn(connectedEngine)
        projectSettings.isBindingEnabled = true
        projectSettings.connectionName = "server1"
        projectSettings.projectKey = "key"

        assertThat(moduleBindingManager.engineIfStarted).isEqualTo(connectedEngine)
    }

    @Test
    fun should_return_connected_engine_if_no_default_binding_but_bound_on_module_level() {
        `when`(engineManager.getConnectedEngineIfStarted(ArgumentMatchers.anyString())).thenReturn(connectedEngine)
        projectSettings.isBindingEnabled = true
        projectSettings.connectionName = "server1"
        moduleSettings.bindTo("key")

        assertThat(moduleBindingManager.engineIfStarted).isEqualTo(connectedEngine)
    }

    private val standaloneEngine = mock(StandaloneSonarLintEngine::class.java)
    private val connectedEngine = mock(ConnectedSonarLintEngine::class.java)
    private val engineManager = mock(SonarLintEngineManager::class.java)


}
