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
package org.sonarlint.intellij.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.util.Function
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.runOnPooledThread

class ModuleChangeListener(val project: Project) : ModuleListener {

    override fun modulesAdded(project: Project, modules: List<Module>) {
        runOnPooledThread(project) { getService(BackendService::class.java).modulesAdded(project, modules) }
    }

    override fun moduleRemoved(project: Project, module: Module) {
        runOnPooledThread(project) { getService(BackendService::class.java).moduleRemoved(module) }
    }

    override fun modulesRenamed(project: Project, modules: MutableList<out Module>, oldNameProvider: Function<in Module, String>) {
        val projectSettings = getSettingsFor(project)
        val moduleMapping = projectSettings.moduleMapping
        for (module in modules) {
            val previousModuleName = oldNameProvider.`fun`(module)
            if (moduleMapping[previousModuleName] != null) {
                moduleMapping[module.name] = moduleMapping.remove(previousModuleName)
            } else {
                moduleMapping[module.name] = previousModuleName
            }
        }
    }

}
