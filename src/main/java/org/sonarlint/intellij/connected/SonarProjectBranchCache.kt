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
package org.sonarlint.intellij.connected

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class SonarProjectBranchCache(private val project: Project) {
    private val matchedBranchPerModule = mutableMapOf<Module, String>()
    private val matchedBranchPerProject = mutableMapOf<Project, String>()
    fun setMatchedBranch(module: Module, branchName: String) {
        matchedBranchPerModule[module] = branchName
        project.messageBus.syncPublisher(SonarProjectBranchListener.TOPIC).matchedBranchChanged(module, branchName)
    }

    fun setMatchedBranch(project: Project, branchName: String) {
        matchedBranchPerProject[project] = branchName
    }

    fun getMatchedBranch(module: Module): String? {
        return matchedBranchPerModule[module]
    }
}