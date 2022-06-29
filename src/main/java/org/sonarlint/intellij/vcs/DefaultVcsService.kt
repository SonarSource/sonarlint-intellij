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
import com.intellij.serviceContainer.NonInjectable
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.vcs.ModuleVcsRepoProvider
import org.sonarlint.intellij.common.vcs.VcsListener.TOPIC
import org.sonarlint.intellij.common.vcs.VcsService
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.messages.ProjectBindingListener
import org.sonarlint.intellij.messages.ProjectSynchronizationListener
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultVcsService @NonInjectable constructor(private val project: Project, private val executor: ExecutorService) : VcsService, Disposable {
    private val logger = SonarLintLogger.get()
    private val resolvedBranchPerModule: MutableMap<Module, String?> = mutableMapOf()

    constructor(project: Project) : this(project, Executors.newSingleThreadExecutor())

    override fun getServerBranchName(module: Module): String? {
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
        val repositoriesEPs = ModuleVcsRepoProvider.EP_NAME.extensionList
        val repositories = repositoriesEPs.mapNotNull { it.getRepoFor(module, logger) }.toList()
        if (repositories.isEmpty()) {
            logger.warn("No VCS repository found for module $module")
            return null
        }
        if (repositories.size > 1) {
            logger.warn("Several candidate Vcs repositories detected for module $module, cannot choose one")
            return null
        }
        val repo = repositories.first()
        return repo.electBestMatchingServerBranchForCurrentHead(validConnectedEngine.getServerBranches(projectKey))
    }

    override fun clearCache() {
        resolvedBranchPerModule.clear()
    }

    override fun refreshCacheAsync() {
        if (!executor.isShutdown) {
            executor.execute { refreshCache() }
        }
    }

    private fun refreshCache() {
        resolvedBranchPerModule.forEach { (module, previousBranchName) ->
            val newBranchName = resolveServerBranchName(module)
            resolvedBranchPerModule[module] = newBranchName
            if (previousBranchName != newBranchName) {
                project.messageBus.syncPublisher(TOPIC).resolvedServerBranchChanged(module, newBranchName)
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

class RefreshCacheOnProjectSync(private val project: Project) : ProjectSynchronizationListener {
    override fun synchronizationFinished() {
        val vcsService = getService(project, VcsService::class.java)
        vcsService.refreshCacheAsync()
    }
}

