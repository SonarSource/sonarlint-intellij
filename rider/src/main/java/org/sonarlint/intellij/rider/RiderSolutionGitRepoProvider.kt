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
package org.sonarlint.intellij.rider

import com.intellij.openapi.module.Module
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.ProjectModelEntityVisitor
import com.jetbrains.rider.projectView.workspace.getVirtualFileAsContentRoot
import com.jetbrains.rider.projectView.workspace.isProjectFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.FileUtils.Companion.isFileValidForSonarLint
import org.sonarlint.intellij.common.vcs.ModuleVcsRepoProvider
import org.sonarlint.intellij.common.vcs.VcsRepo
import org.sonarlint.intellij.git.GitRepo

class RiderSolutionGitRepoProvider : ModuleVcsRepoProvider {
    override fun getRepoFor(module: Module): VcsRepo? {
        val repositoryManager = try {
            GitRepositoryManager.getInstance(module.project)
        } catch (e: NoClassDefFoundError) {
            return null
        }

        val moduleRepositories = mutableSetOf<GitRepository>()
        val visitor = object : ProjectModelEntityVisitor() {
            override fun visitProjectFile(entity: ProjectModelEntity): Result {
                if (module.isDisposed) {
                    return Result.Stop
                }

                if (entity.isProjectFile()) {
                    entity.getVirtualFileAsContentRoot()?.let {
                        if (!it.isDirectory && it.isValid && isFileValidForSonarLint(it, module.project)) {
                            repositoryManager.getRepositoryForFile(it)?.let { repo -> moduleRepositories.add(repo) }
                        }
                    }
                }

                return Result.Continue
            }
        }
        visitor.visit(module.project)

        if (moduleRepositories.isEmpty()) {
            return null
        }
        if (moduleRepositories.size > 1) {
            SonarLintConsole.get(module.project)
                .info("Several candidate Git repositories detected for module $module in Rider, cannot resolve branch")
            return null
        }
        return GitRepo(moduleRepositories.first(), module.project)
    }
}