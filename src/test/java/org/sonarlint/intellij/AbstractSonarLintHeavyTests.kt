/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.ComponentManagerImpl
import org.junit.jupiter.api.BeforeEach
import org.sonarlint.intellij.SonarLintTestUtils.clearServerConnectionCredentials
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.config.project.SonarLintProjectSettings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.test.AbstractHeavyTests

abstract class AbstractSonarLintHeavyTests : AbstractHeavyTests() {
    
    @BeforeEach
    open fun clearCredentials() {
        clearServerConnectionCredentials()
    }

    val globalSettings: SonarLintGlobalSettings
        get() {
            return Settings.getGlobalSettings()
        }

    val projectSettings: SonarLintProjectSettings
        get() {
            return Settings.getSettingsFor(project)
        }

    protected fun connectModuleTo(projectKey: String) {
        connectModuleTo(module, projectKey)
    }

    protected fun connectModuleTo(module: Module, projectKey: String) {
        Settings.getSettingsFor(module).projectKey = projectKey
    }

    protected fun connectProjectTo(connection: ServerConnection, projectKey: String) {
        Settings.getGlobalSettings().addServerConnection(connection)
        Settings.getSettingsFor(project).bindTo(connection, projectKey)
    }

    protected fun unbindProject() {
        getService(project, ProjectBindingManager::class.java).unbind()
    }

    protected open fun <T : Any> replaceProjectService(clazz: Class<T>, newInstance: T) {
        (project as ComponentManagerImpl).replaceServiceInstance(clazz, newInstance, project)
    }

    protected fun projectBackendId(project: Project) = project.projectFilePath!!
    protected fun moduleBackendId(module: Module) = BackendService.moduleId(module)

}

