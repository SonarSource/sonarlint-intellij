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
package org.sonarlint.intellij.git

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import git4idea.repo.GitRepositoryManager
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.vcs.ModuleVcsRepoProvider
import org.sonarlint.intellij.common.vcs.VcsRepo

class ModuleGitRepoProvider : ModuleVcsRepoProvider {
    override fun getRepoFor(module: Module): VcsRepo? {
        val repositoryManager = GitRepositoryManager.getInstance(module.project)
        val moduleRepositories = ModuleRootManager.getInstance(module)
            .contentRoots
            .mapNotNull { root -> repositoryManager.getRepositoryForFile(root) }
            .toSet()
        if (moduleRepositories.isEmpty()) {
            return null
        }
        if (moduleRepositories.size > 1) {
            SonarLintConsole.get(module.project).info("Several candidate Git repositories detected for module $module, cannot resolve branch")
            return null
        }
        return GitRepo(moduleRepositories.first(), module.project)
    }
}
