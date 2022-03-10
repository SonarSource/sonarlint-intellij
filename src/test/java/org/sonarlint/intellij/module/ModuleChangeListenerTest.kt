/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.module

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.capture
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.eq
import org.sonarlint.intellij.messages.PROJECT_BINDING_TOPIC
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine

@RunWith(MockitoJUnitRunner::class)
class ModuleChangeListenerTest : AbstractSonarLintLightTests() {
    @Before
    fun prepare() {
        replaceProjectService(ProjectBindingManager::class.java, projectBindingManager)
        replaceModuleService(ModuleBindingManager::class.java, moduleBindingManager)
        `when`(moduleBindingManager.engineIfStarted).thenReturn(moduleFakeEngine)
        moduleChangeListener = ModuleChangeListener(project)
    }

    @Test
    fun should_declare_module_on_engine_when_module_added_to_project() {
        moduleChangeListener.moduleAdded(project, module)

        verify(moduleFakeEngine).declareModule(capture(moduleInfoCaptor))
        val moduleInfo = moduleInfoCaptor.value
        assertThat(moduleInfo.key()).isEqualTo(module)
    }

    @Test
    fun should_stop_module_on_engine_when_module_removed_from_project() {
        moduleChangeListener.moduleRemoved(project, module)

        verify(moduleFakeEngine).stopModule(eq(module))
    }

    @Test
    fun should_stop_modules_on_engine_when_project_is_closed() {
        ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectClosing(project)

        verify(moduleFakeEngine).stopModule(eq(module))
    }

    @Test
    fun should_stop_and_recreate_modules_when_binding_changes() {
        moduleChangeListener.moduleAdded(project, module)
        val standaloneEngine = mock(StandaloneSonarLintEngine::class.java)
        val connectedEngine = mock(ConnectedSonarLintEngine::class.java)
        engineManager.registerEngine(standaloneEngine)
        engineManager.registerEngine(connectedEngine, "connection1")
        project.messageBus.syncPublisher(PROJECT_BINDING_TOPIC)
            .bindingChanged(null, ProjectBinding("connection1", "projectKey", emptyMap()))

        // might be called several times by the real implementation, not the one under test
        verify(standaloneEngine, atLeastOnce()).stopModule(eq(module))
        verify(connectedEngine, atLeastOnce()).declareModule(capture(moduleInfoCaptor))
        val moduleInfo = moduleInfoCaptor.value
        assertThat(moduleInfo.key()).isEqualTo(module)
    }

    private lateinit var moduleChangeListener: ModuleChangeListener

    @Mock
    private lateinit var projectBindingManager: ProjectBindingManager

    @Mock
    private lateinit var moduleFakeEngine: ConnectedSonarLintEngine

    @Mock
    private lateinit var moduleBindingManager: ModuleBindingManager

    @Captor
    private lateinit var moduleInfoCaptor: ArgumentCaptor<ClientModuleInfo>
}
