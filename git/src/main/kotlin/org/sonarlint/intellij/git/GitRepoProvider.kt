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
package org.sonarlint.intellij.git

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.vcs.VcsRepo
import org.sonarlint.intellij.common.vcs.VcsRepoProvider

class GitRepoProvider : VcsRepoProvider {
    override fun getRepoFor(module: Module): VcsRepo? {
        val repositoryManager = GitRepositoryManager.getInstance(module.project)
        val moduleRepositories = findRepoFor(repositoryManager, module)
        if (moduleRepositories.isEmpty()) {
            return null
        }
        if (moduleRepositories.size > 1) {
            SonarLintConsole.get(module.project).info("Several candidate Git repositories detected for module $module, cannot resolve branch")
            return null
        }
        return GitRepo(moduleRepositories.first(), module.project)
    }

    override fun getRepoFor(project: Project): VcsRepo? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val moduleRepositories = project.modules.flatMap {
            findRepoFor(repositoryManager, it)
        }.toSet()
        if (moduleRepositories.isEmpty()) {
            return null
        }
        if (moduleRepositories.size > 1) {
            SonarLintConsole.get(project)
                .info("Several candidate Git repositories detected for project $project, cannot resolve branch")
            return null
        }
        return GitRepo(moduleRepositories.first(), project)
    }

    private fun findRepoFor(repositoryManager: GitRepositoryManager, module: Module): Set<GitRepository> {
        return ModuleRootManager.getInstance(module)
            .contentRoots
            .mapNotNull { root -> repositoryManager.getRepositoryForFile(root) }
            .toSet()
    }
}
