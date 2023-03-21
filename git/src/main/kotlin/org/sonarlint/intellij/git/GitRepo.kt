/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import org.sonarlint.intellij.common.vcs.VcsRepo
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger

class GitRepo(private val repo: GitRepository, private val project: Project, private val logger: SonarLintLogger) : VcsRepo {
    override fun electBestMatchingServerBranchForCurrentHead(projectBranches: ProjectBranches): String? {
        val serverCandidateNames = projectBranches.branchNames
        val serverMainBranch = projectBranches.mainBranchName
        return try {
            val currentBranch = repo.currentBranchName
            if (currentBranch != null && serverCandidateNames.contains(currentBranch)) {
                return currentBranch
            }
            val head = repo.currentRevision ?: return null // Could be the case if no commit has been made in the repo

            val branchesPerDistance: MutableMap<Int, MutableSet<String>> = HashMap()
            for (serverBranchName in serverCandidateNames) {
                val localBranch = repo.branches.findLocalBranch(serverBranchName) ?: continue
                val localBranchHash = repo.branches.getHash(localBranch) ?: continue
                val distance = distance(project, repo, head, localBranchHash.asString()) ?: continue
                branchesPerDistance.computeIfAbsent(distance) { HashSet() }.add(serverBranchName)
            }
            if (branchesPerDistance.isEmpty()) {
                return null
            }
            val minDistance = branchesPerDistance.keys.stream().min(Comparator.naturalOrder()).get()
            val bestCandidates: Set<String?> = branchesPerDistance[minDistance]!!
            logger.info("Best candidates are $bestCandidates")

            if (bestCandidates.contains(serverMainBranch)) {
                // Favor the main branch when there are multiple candidates with the same distance
                serverMainBranch
            } else bestCandidates.first()
        } catch (e: Exception) {
            logger.error("Couldn't find best matching branch", e)
            null
        }
    }

    private fun distance(project: Project, repository: GitRepository, from: String, to: String): Int? {
        logger.info("Getting merge base from HEAD=$from to $to")
        val mergeBase = GitHistoryUtils.getMergeBase(project, repository.root, from, to) ?: return null
        logger.info("Found merge base ${mergeBase.asString()}")
        val aheadCount = getNumberOfCommitsBetween(repository, from, mergeBase.asString()) ?: return null
        logger.info("Number of commits ahead=$aheadCount ")
        val behindCount = getNumberOfCommitsBetween(repository, to, mergeBase.asString()) ?: return null
        logger.info("Number of commits behind=$behindCount ")
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
