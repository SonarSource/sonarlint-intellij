/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.vcs

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.serviceContainer.NonInjectable
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.messages.ProjectBindingListener
import org.sonarlint.intellij.messages.ProjectSynchronizationListener
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VcsService @NonInjectable constructor(private val project: Project, private val executor: ExecutorService) : Disposable {
    private val logger = SonarLintLogger.get()
    private val resolvedBranchPerModule: MutableMap<Module, String?> = mutableMapOf()

    constructor(project: Project) : this(project, Executors.newSingleThreadExecutor())

    fun getServerBranchName(module: Module): String? {
        if (resolvedBranchPerModule.containsKey(module)) {
            return resolvedBranchPerModule[module]
        }
        val branchName = resolveServerBranchName(module)
        resolvedBranchPerModule[module] = branchName
        return branchName
    }

    private fun resolveServerBranchName(module: Module): String? {
        val bindingManager = getService(project, ProjectBindingManager::class.java)
        val validConnectedEngine = bindingManager.validConnectedEngine ?: return null
        val projectKey = getService(module, ModuleBindingManager::class.java).resolveProjectKey() ?: return null
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val moduleRepositories = ModuleRootManager.getInstance(module)
            .contentRoots
            .mapNotNull { root -> repositoryManager.getRepositoryForRoot(root) }
            .toSet()
        if (moduleRepositories.isEmpty()) {
            return null
        }
        if (moduleRepositories.size > 1) {
            logger.warn("Several candidate Git repositories detected for module $module, cannot resolve branch")
            return null
        }

        val serverBranches = validConnectedEngine.getServerBranches(projectKey)
        val repository = moduleRepositories.first()
        return electBestMatchingServerBranchForCurrentHead(
            repository, serverBranches.branchNames, serverBranches.mainBranchName.orElse(null)
        )
    }

    private fun electBestMatchingServerBranchForCurrentHead(
        repo: GitRepository, serverCandidateNames: Set<String>, serverMainBranch: String?
    ): String? {
        return try {
            val currentBranch = repo.currentBranchName
            if (currentBranch != null && serverCandidateNames.contains(currentBranch)) {
                return currentBranch
            }
            val head = repo.currentRevision ?: // Could be the case if no commit has been made in the repo
            return null
            val branchesPerDistance: MutableMap<Int, MutableSet<String>> = HashMap()
            for (serverBranchName in serverCandidateNames) {
                val localBranch = repo.branches.findLocalBranch(serverBranchName) ?: continue
                val localBranchHash = repo.branches.getHash(localBranch) ?: continue
                val distance = GitUtils.distance(project, repo, head, localBranchHash.asString()) ?: continue
                branchesPerDistance.computeIfAbsent(distance) { HashSet() }.add(serverBranchName)
            }
            if (branchesPerDistance.isEmpty()) {
                return null
            }
            val minDistance = branchesPerDistance.keys.stream().min(Comparator.naturalOrder()).get()
            val bestCandidates: Set<String?> = branchesPerDistance[minDistance]!!
            if (serverMainBranch != null && bestCandidates.contains(serverMainBranch)) {
                // Favor the main branch when there are multiple candidates with the same distance
                serverMainBranch
            } else bestCandidates.first()
        } catch (e: Exception) {
            logger.error("Couldn't find best matching branch", e)
            null
        }
    }

    internal fun clearCache() {
        resolvedBranchPerModule.clear()
    }

    internal fun refreshCacheAsync() {
        if (!executor.isShutdown) {
            executor.execute { refreshCache() }
        }
    }

    private fun refreshCache() {
        resolvedBranchPerModule.forEach { (module, previousBranchName) ->
            val newBranchName = resolveServerBranchName(module)
            resolvedBranchPerModule[module] = newBranchName
            if (previousBranchName != newBranchName) {
                project.messageBus.syncPublisher(VCS_TOPIC).resolvedServerBranchChanged(module, newBranchName)
            }
        }
    }

    override fun dispose() {
        executor.shutdownNow()
    }
}

class RefreshCacheOnBindingChange(private val project: Project) : ProjectBindingListener {
    override fun bindingChanged(previousBinding: ProjectBinding?, newBinding: ProjectBinding?) {
        val vcsService = getService(project, VcsService::class.java)
        if (newBinding == null) {
            vcsService.clearCache()
        } else {
            vcsService.refreshCacheAsync()
        }
    }
}

class RefreshCacheOnRepositoryChange(private val project: Project) : GitRepositoryChangeListener {
    override fun repositoryChanged(repository: GitRepository) {
        val vcsService = getService(project, VcsService::class.java)
        vcsService.refreshCacheAsync()
    }
}

class RefreshCacheOnProjectSync(private val project: Project) : ProjectSynchronizationListener {
    override fun synchronizationFinished() {
        val vcsService = getService(project, VcsService::class.java)
        vcsService.refreshCacheAsync()
    }
}

