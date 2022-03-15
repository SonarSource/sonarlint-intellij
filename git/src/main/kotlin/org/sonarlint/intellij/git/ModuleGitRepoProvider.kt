package org.sonarlint.intellij.git

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import git4idea.repo.GitRepositoryManager
import org.sonarlint.intellij.common.vcs.ModuleVcsRepoProvider
import org.sonarlint.intellij.common.vcs.VcsRepo
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger

class ModuleGitRepoProvider : ModuleVcsRepoProvider {
    override fun getRepoFor(module: Module, logger: SonarLintLogger): VcsRepo? {
        val repositoryManager = GitRepositoryManager.getInstance(module.project)
        val moduleRepositories = ModuleRootManager.getInstance(module)
            .contentRoots
            .mapNotNull { root -> repositoryManager.getRepositoryForFile(root) }
            .toSet()
        if (moduleRepositories.isEmpty()) {
            return null
        }
        if (moduleRepositories.size > 1) {
            logger.warn("Several candidate Git repositories detected for module $module, cannot resolve branch")
            return null
        }
        return GitRepo(moduleRepositories.first(), module.project, logger)
    }
}
