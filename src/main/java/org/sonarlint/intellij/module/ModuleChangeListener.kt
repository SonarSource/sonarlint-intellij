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

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.Function
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.EngineManager
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.messages.ProjectBindingListener
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine

private fun getEngineIfStarted(project: Project) = getService(project, ProjectBindingManager::class.java).engineIfStarted

class ModuleChangeListener(val project: Project) : ModuleListener {

    override fun modulesAdded(project: Project, modules: List<Module>) {
        val engine = getEngineIfStarted(project)
        modules.forEach { Modules.declareModule(project, engine, it) }
        getService(BackendService::class.java).modulesAdded(project, modules)
    }

    override fun moduleRemoved(project: Project, module: Module) {
        Modules.removeModule(getEngineIfStarted(module.project), module)
        getService(BackendService::class.java).moduleRemoved(module)
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

private object Modules {
    fun declareAllModules(project: Project, engine: SonarLintAnalysisEngine?) {
        ModuleManager.getInstance(project).modules.forEach { declareModule(project, engine, it) }
    }

    fun declareModule(project: Project, engine: SonarLintAnalysisEngine?, module: Module) {
        val moduleInfo = ClientModuleInfo(module, ModuleFileSystem(project, module))
        getService(ModulesRegistry::class.java).add(module, moduleInfo)
        engine?.declareModule(moduleInfo)
    }

    fun removeModule(engine: SonarLintAnalysisEngine?, module: Module) {
        engine?.stopModule(module)
        getService(ModulesRegistry::class.java).remove(module)
    }

    fun removeAllModules(project: Project, engine: SonarLintAnalysisEngine?) {
        ModuleManager.getInstance(project).modules.forEach { removeModule(engine, it) }
    }
}

class ProjectClosedListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        val engine = getEngineIfStarted(project)
        ModuleManager.getInstance(project).modules.forEach {
            Modules.removeModule(engine, it)
        }
    }
}

class MoveModulesOnBindingChange(private val project: Project) : ProjectBindingListener {
    override fun bindingChanged(previousBinding: ProjectBinding?, newBinding: ProjectBinding?) {
        if (previousBinding?.connectionName != newBinding?.connectionName) {
            Modules.removeAllModules(project, getEngineIfStarted(previousBinding))
            Modules.declareAllModules(project, getEngineIfStarted(newBinding))
        }
    }

    private fun getEngineIfStarted(binding: ProjectBinding?): SonarLintAnalysisEngine? {
        val engineManager = getService(EngineManager::class.java)
        return if (binding == null) engineManager.standaloneEngineIfStarted
        else engineManager.getConnectedEngineIfStarted(binding.connectionName)
    }
}
