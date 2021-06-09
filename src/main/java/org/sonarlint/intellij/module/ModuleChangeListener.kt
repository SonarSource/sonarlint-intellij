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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo

class ModuleChangeListener : ModuleListener {

    override fun moduleAdded(project: Project, module: Module) {
        declareModule(project, module)
    }

    override fun moduleRemoved(project: Project, module: Module) {
        removeModule(project, module)
    }

    companion object {
        init {
            ApplicationManager.getApplication().messageBus.connect()
                .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                    override fun projectClosed(project: Project) {
                        ModuleManager.getInstance(project).modules.forEach { removeModule(project, it) }
                    }
                })
        }

        fun declareModule(project: Project, module: Module) {
            val moduleInfo = ModuleInfo(module, ModuleFileSystem(project, module))
            getService(ModulesRegistry::class.java).add(module, moduleInfo)
            getEngine(project)?.declareModule(moduleInfo)
        }

        fun removeModule(project: Project, module: Module) {
            getEngine(project)?.stopModule(module)
            getService(ModulesRegistry::class.java).remove(module)
        }

        private fun getEngine(project: Project) =
            getService(project, ProjectBindingManager::class.java).engineIfStarted
    }
}
