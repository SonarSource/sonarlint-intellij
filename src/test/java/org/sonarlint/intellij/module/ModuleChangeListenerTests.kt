/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.eq
import org.sonarlint.intellij.messages.PROJECT_BINDING_TOPIC
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine

class ModuleChangeListenerTests : AbstractSonarLintLightTests() {
    @BeforeEach
    fun prepare() {
        replaceProjectService(ProjectBindingManager::class.java, projectBindingManager)
        replaceModuleService(ModuleBindingManager::class.java, moduleBindingManager)
        whenever(projectBindingManager.engineIfStarted).thenReturn(projectFakeEngine)
        moduleChangeListener = ModuleChangeListener(project)
    }

    @Test
    fun should_declare_module_on_engine_when_module_added_to_project() {
        moduleChangeListener.modulesAdded(project, listOf(module))

        argumentCaptor<ClientModuleInfo>().apply {
            verify(projectFakeEngine).declareModule(capture())
            assertThat(firstValue.key()).isEqualTo(module)
        }
    }

    @Test
    fun should_stop_module_on_engine_when_module_removed_from_project() {
        moduleChangeListener.moduleRemoved(project, module)

        verify(projectFakeEngine).stopModule(eq(module))
    }

    @Test
    fun should_stop_modules_on_engine_when_project_is_closed() {
        ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectClosing(project)

        verify(projectFakeEngine).stopModule(eq(module))
    }

    @Test
    fun should_stop_and_recreate_modules_when_binding_changes() {
        moduleChangeListener.modulesAdded(project, listOf(module))
        val standaloneEngine = mock(SonarLintAnalysisEngine::class.java)
        val connectedEngine = mock(SonarLintAnalysisEngine::class.java)
        getEngineManager().registerEngine(standaloneEngine)
        getEngineManager().registerEngine(connectedEngine, "connection1")
        project.messageBus.syncPublisher(PROJECT_BINDING_TOPIC)
            .bindingChanged(null, ProjectBinding("connection1", "projectKey", emptyMap()))

        // might be called several times by the real implementation, not the one under test
        verify(standaloneEngine, atLeastOnce()).stopModule(eq(module))
        argumentCaptor<ClientModuleInfo>().apply {
            verify(connectedEngine, atLeastOnce()).declareModule(capture())
            assertThat(firstValue.key()).isEqualTo(module)
        }
    }

    private lateinit var moduleChangeListener: ModuleChangeListener
    private var projectBindingManager: ProjectBindingManager = mock()
    private var projectFakeEngine: SonarLintAnalysisEngine = mock()
    private var moduleBindingManager: ModuleBindingManager = mock()
}
