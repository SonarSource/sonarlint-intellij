/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.binding

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionSuggestionDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarQubeConnectionSuggestionDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion

class BindingSuggestionHandlerTests : AbstractSonarLintLightTests() {

    private fun mockModule(name: String): Module {
        val module = Mockito.mock(Module::class.java)
        Mockito.`when`(module.name).thenReturn(name)
        return module
    }

    private fun mockProject(name: String): Project {
        val project = Mockito.mock(Project::class.java)
        Mockito.`when`(project.name).thenReturn(name)
        return project
    }

    @Test
    fun `should find overridden modules for SonarQube Server`() {
        val module1 = mockModule("module1")
        val project = mockProject("project")
        val projectBinding = ClientBindingSuggestion(
            "scopeId",
            project,
            null,
            ConnectionSuggestionDto(SonarQubeConnectionSuggestionDto("url", "projectKey1"), BindingSuggestionOrigin.PROPERTIES_FILE)
        )
        val moduleSuggestion = ClientBindingSuggestion(
            "scopeIdModule",
            project,
            module1,
            ConnectionSuggestionDto(SonarQubeConnectionSuggestionDto("url", "projectKey2"), BindingSuggestionOrigin.PROPERTIES_FILE)
        )
        val suggestions = listOf(projectBinding, moduleSuggestion)

        val result = BindingSuggestionHandler.findOverriddenModules(suggestions, projectBinding)

        assertThat(result).containsEntry(module1, "projectKey2")
    }

    @Test
    fun `should find overridden modules for SonarQube Cloud`() {
        val module1 = mockModule("module1")
        val project = mockProject("project")
        val cloudSuggestion = ConnectionSuggestionDto(SonarCloudConnectionSuggestionDto("org", "projectKey1", SonarCloudRegion.EU), BindingSuggestionOrigin.PROPERTIES_FILE)
        val projectBinding = ClientBindingSuggestion(
            "scopeId",
            project,
            null,
            cloudSuggestion
        )
        val cloudSuggestion2 = ConnectionSuggestionDto(SonarCloudConnectionSuggestionDto("org", "projectKey2", SonarCloudRegion.EU), BindingSuggestionOrigin.PROPERTIES_FILE)
        val moduleSuggestion = ClientBindingSuggestion(
            "scopeIdModule",
            project,
            module1,
            cloudSuggestion2
        )
        val suggestions = listOf(projectBinding, moduleSuggestion)

        val result = BindingSuggestionHandler.findOverriddenModules(suggestions, projectBinding)

        assertThat(result).containsEntry(module1, "projectKey2")
    }

    @Test
    fun `should not find overridden modules if projectKey is the same`() {
        val module1 = mockModule("module1")
        val project = mockProject("project")
        val projectBinding = ClientBindingSuggestion(
            "scopeId",
            project,
            null,
            ConnectionSuggestionDto(SonarQubeConnectionSuggestionDto("url", "projectKey1"), BindingSuggestionOrigin.PROPERTIES_FILE)
        )
        val moduleSuggestion = ClientBindingSuggestion(
            "scopeIdModule",
            project,
            module1,
            ConnectionSuggestionDto(SonarQubeConnectionSuggestionDto("url", "projectKey1"), BindingSuggestionOrigin.PROPERTIES_FILE)
        )
        val suggestions = listOf(projectBinding, moduleSuggestion)

        val result = BindingSuggestionHandler.findOverriddenModules(suggestions, projectBinding)

        assertThat(result).isEmpty()
    }

    @Test
    fun `should not find overridden modules if serverUrl or organization is different`() {
        val module1 = mockModule("module1")
        val project = mockProject("project")
        val projectBinding = ClientBindingSuggestion(
            "scopeId",
            project,
            null,
            ConnectionSuggestionDto(SonarCloudConnectionSuggestionDto("url1", "projectKey1", SonarCloudRegion.EU), BindingSuggestionOrigin.PROPERTIES_FILE)
        )
        val moduleSuggestion = ClientBindingSuggestion(
            "scopeIdModule",
            project,
            module1,
            ConnectionSuggestionDto(SonarCloudConnectionSuggestionDto("url2", "projectKey2", SonarCloudRegion.EU), BindingSuggestionOrigin.PROPERTIES_FILE)
        )
        val suggestions = listOf(projectBinding, moduleSuggestion)

        val result = BindingSuggestionHandler.findOverriddenModules(suggestions, projectBinding)

        assertThat(result).isEmpty()
    }

}
