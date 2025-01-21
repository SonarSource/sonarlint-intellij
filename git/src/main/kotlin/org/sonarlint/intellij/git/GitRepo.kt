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

import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import java.nio.file.Path
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.vcs.VcsRepo

class GitRepo(private val repo: GitRepository, private val project: Project) : VcsRepo {
    override fun electBestMatchingServerBranchForCurrentHead(mainBranchName: String, allBranchNames: Set<String>): String? {
        return try {
            val currentBranch = repo.currentBranchName
            if (currentBranch != null && currentBranch in allBranchNames) {
                return currentBranch
            }
            val head = repo.currentRevision ?: return null // Could be the case if no commit has been made in the repo

            val branchesPerDistance: MutableMap<Int, MutableSet<String>> = HashMap()
            for (serverBranchName in allBranchNames) {
                val localBranch = repo.branches.findLocalBranch(serverBranchName) ?: continue
                val localBranchHash = repo.branches.getHash(localBranch) ?: continue
                val distance = distance(project, repo, head, localBranchHash.asString()) ?: continue
                branchesPerDistance.computeIfAbsent(distance) { HashSet() }.add(serverBranchName)
            }
            val bestCandidates = branchesPerDistance.minByOrNull { it.key }?.value ?: return null
            if (mainBranchName in bestCandidates) {
                // Favor the main branch when there are multiple candidates with the same distance
                mainBranchName
            } else bestCandidates.first()
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Couldn't find best matching branch", e)
            null
        }
    }

    override fun isBranchMatchingCurrentHead(branch: String): Boolean {
        return try {
            return repo.currentBranchName == branch
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Couldn't compare branches", e)
            false
        }
    }

    override fun getGitDir(): Path? {
        return try {
            repo.root.toNioPath()
        } catch (e: UnsupportedOperationException) {
            null
        }
    }

    private fun distance(project: Project, repository: GitRepository, from: String, to: String): Int? {
        val mergeBase = GitHistoryUtils.getMergeBase(project, repository.root, from, to) ?: return null
        val aheadCount = getNumberOfCommitsBetween(repository, from, mergeBase.asString()) ?: return null
        val behindCount = getNumberOfCommitsBetween(repository, to, mergeBase.asString()) ?: return null
        return aheadCount + behindCount
    }

    private fun getNumberOfCommitsBetween(
        repository: GitRepository,
        from: String,
        to: String,
    ): Int? {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.REV_LIST)
        handler.addParameters("--count", "$from..$to")
        handler.setSilent(true)
        return try {
            Integer.parseInt(Git.getInstance().runCommand(handler).getOutputOrThrow().trim())
        } catch (e: Exception) {
            throw Exception("Cannot get number of commits between '$from' and '$to'", e)
        }
    }
}
