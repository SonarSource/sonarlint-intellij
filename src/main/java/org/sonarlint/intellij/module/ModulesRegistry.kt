/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.module.Module
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo

class ModulesRegistry {
    private val modules: MutableMap<Module, ModuleInfo> = LinkedHashMap()

    fun getStandaloneModules(): List<ModuleInfo> {
        return modules.filterKeys { connectionIdFor(it) == null }
            .values.toList()
    }

    fun getModulesForEngine(connectionId: String): List<ModuleInfo> {
        return modules.filterKeys { connectionIdFor(it) == connectionId }
            .values.toList()
    }

    fun add(module: Module, moduleInfo: ModuleInfo) {
        modules[module] = moduleInfo
    }

    fun remove(module: Module) {
        modules.remove(module)
    }

    private fun connectionIdFor(module: Module) =
        getService(module.project, ProjectBindingManager::class.java).tryGetServerConnection().map { it.name }
            .orElse(null)
}
