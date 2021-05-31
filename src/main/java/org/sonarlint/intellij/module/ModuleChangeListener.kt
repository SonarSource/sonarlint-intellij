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
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.util.SonarLintUtils
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo
import org.sonarsource.sonarlint.core.client.api.common.ModulesProvider
import java.lang.IllegalStateException
import java.util.Collections
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

class ModuleChangeListener : ModuleListener, ModulesProvider {
  private val modules: MutableMap<Module, ModuleInfo> = LinkedHashMap()
  override fun moduleAdded(project: Project, module: Module) {
    val moduleInfo = ModuleInfo(module, ModuleFileSystem(project, module))
    modules[module] = moduleInfo
    getEngine(project).declareModule(moduleInfo)
  }

  override fun moduleRemoved(project: Project, module: Module) {
    getEngine(project).stopModule(module)
    modules.remove(module)
  }

  override fun getModules(): List<ModuleInfo> {
    return Collections.unmodifiableList(ArrayList(modules.values))
  }

  companion object {
    private fun getEngine(project: Project): SonarLintEngine {
      return try {
        val projectBindingManager = SonarLintUtils.getService(project, ProjectBindingManager::class.java)
        projectBindingManager.getFacade(true).engine
      } catch (e: InvalidBindingException) {
        throw IllegalStateException("Could not get engine for project $project", e)
      }
    }
  }
}
