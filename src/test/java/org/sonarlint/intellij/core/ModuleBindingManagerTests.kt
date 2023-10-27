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

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.fixtures.newSonarQubeConnection
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine

class ModuleBindingManagerTests : AbstractSonarLintLightTests() {

    private lateinit var moduleBindingManager: ModuleBindingManager

    @BeforeEach
    fun prepare() {
        standaloneEngine = mock(StandaloneSonarLintEngine::class.java)
        connectedEngine = mock(ConnectedSonarLintEngine::class.java)
        getEngineManager().registerEngine(standaloneEngine)
        getEngineManager().registerEngine(connectedEngine, "server1")
        moduleBindingManager = ModuleBindingManager(module)
        replaceModuleService(ModuleBindingManager::class.java, moduleBindingManager)
    }

    @Test
    fun should_resolve_project_key_if_module_is_not_bound() {
        projectSettings.bindTo(newSonarQubeConnection("name"), "projectKey")

        val resolvedProjectKey = moduleBindingManager.resolveProjectKey()

        assertThat(resolvedProjectKey).isEqualTo("projectKey")
    }

    @Test
    fun should_not_resolve_any_key_if_project_and_module_are_not_bound() {
        val resolvedProjectKey = moduleBindingManager.resolveProjectKey()

        assertThat(resolvedProjectKey).isNull()
    }

    @Test
    fun should_clear_project_and_module_binding_settings_when_unbinding() {
        moduleSettings.projectKey = "moduleKey"

        moduleBindingManager.unbind()

        assertThat(moduleSettings.isProjectBindingOverridden).isFalse
        assertThat(moduleSettings.projectKey).isEmpty()
    }

    private lateinit var standaloneEngine : StandaloneSonarLintEngine
    private lateinit var connectedEngine : ConnectedSonarLintEngine

}
