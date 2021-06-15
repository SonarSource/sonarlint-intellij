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

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.messages.ProjectEngineListener
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarlint.intellij.util.ThreadPoolExecutor
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine

private fun getEngineIfStarted(project: Project) =
    getService(project, ProjectBindingManager::class.java).engineIfStarted

class ModuleChangeListener(val project: Project) : ModuleListener, Disposable {
    private val connection: MessageBusConnection = project.messageBus.connect()

    init {
        connection.subscribe(
            ProjectEngineListener.TOPIC,
            ProjectEngineListener { previousEngine, newEngine ->
                Modules.removeAllModules(project, previousEngine)
                Modules.declareAllModules(project, newEngine)
            })
    }

    override fun moduleAdded(project: Project, module: Module) {
        Modules.declareModule(project, getEngineIfStarted(project), module)
    }

    override fun moduleRemoved(project: Project, module: Module) {
        Modules.removeModule(getEngineIfStarted(project), module)
    }

    override fun dispose() {
        connection.disconnect()
    }
}

 object Modules {
    private val executor = getService(ThreadPoolExecutor::class.java)

    fun declareAllModules(project: Project, engine: SonarLintEngine?) {
        ModuleManager.getInstance(project).modules.forEach { declareModule(project, engine, it) }
    }

    fun declareModule(project: Project, engine: SonarLintEngine?, module: Module) {
        val moduleInfo = ModuleInfo(module, ModuleFileSystem(project, module))
        getService(ModulesRegistry::class.java).add(module, moduleInfo)
        engine?.let { executor.execute { it.declareModule(moduleInfo) } }
    }

    fun removeModule(engine: SonarLintEngine?, module: Module) {
        engine?.let { executor.execute { it.stopModule(module) } }
        getService(ModulesRegistry::class.java).remove(module)
    }

    fun removeAllModules(project: Project, engine: SonarLintEngine?) {
        ModuleManager.getInstance(project).modules.forEach { removeModule(engine, it) }
    }
}

class ProjectClosedListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        Modules.removeAllModules(project, getEngineIfStarted(project))
    }
}
