/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.annotations.VisibleForTesting
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.vcs.VcsRepo

private data class CacheKey(
    val currentBranch: String?,
    val head: String?,
    val repoBaseDir: Path?,
    val mainBranchName: String,
    val allBranchNames: Set<String>,
)

// Keyed by repository root path so independent repositories (and independent open projects) do not evict each other.
private val cacheByRepoPath = ConcurrentHashMap<String, Pair<CacheKey, String?>>()

@VisibleForTesting
fun clearGitRepoBranchMatchingCache() = cacheByRepoPath.clear()

class GitRepo(private val repo: GitRepository, private val project: Project) : VcsRepo {
    override fun electBestMatchingServerBranchForCurrentHead(mainBranchName: String, allBranchNames: Set<String>): String? {
        val totalStart = System.currentTimeMillis()
        val currentBranch = repo.currentBranchName
        val head = repo.currentRevision
        val repoBaseDir = getGitDir()
        val repoCacheKey = repo.root.path

        val cacheKey = CacheKey(currentBranch, head, repoBaseDir, mainBranchName, allBranchNames)

        cacheByRepoPath[repoCacheKey]?.let { (cachedKey, cachedResult) ->
            if (cachedKey == cacheKey) {
                SonarLintConsole.get(project).debug(
                    "Branch matching cache hit for $repoCacheKey in ${System.currentTimeMillis() - totalStart} ms"
                )
                return cachedResult
            }
        }

        if (currentBranch != null && currentBranch in allBranchNames) {
            cacheByRepoPath[repoCacheKey] = Pair(cacheKey, currentBranch)
            SonarLintConsole.get(project).debug(
                "Branch matching short-circuit (currentBranch in serverBranches) for $repoCacheKey in ${System.currentTimeMillis() - totalStart} ms"
            )
            return currentBranch
        }

        if (head == null) {
            cacheByRepoPath[repoCacheKey] = Pair(cacheKey, null)
            return null // Could be the case if no commit has been made in the repo
        }

        val result = try {
            val branchesPerDistance: MutableMap<Int, MutableSet<String>> = HashMap()
            val branches = repo.branches
            var matchedCount = 0
            for (serverBranchName in allBranchNames) {
                val localBranch = branches.findLocalBranch(serverBranchName) ?: continue
                val localBranchHash = branches.getHash(localBranch) ?: continue
                val distanceStart = System.currentTimeMillis()
                val distance = distance(project, repo, head, localBranchHash.asString()) ?: continue
                SonarLintConsole.get(project).debug(
                    "Branch matching: distance to '$serverBranchName' computed in ${System.currentTimeMillis() - distanceStart} ms"
                )
                matchedCount++
                branchesPerDistance.computeIfAbsent(distance) { HashSet() }.add(serverBranchName)
            }
            SonarLintConsole.get(project).debug(
                "Branch matching walked ${allBranchNames.size} server branches ($matchedCount with local merge-base) for $repoCacheKey in ${System.currentTimeMillis() - totalStart} ms"
            )
            val bestCandidates = branchesPerDistance.minByOrNull { it.key }?.value ?: return null
            if (mainBranchName in bestCandidates) {
                // Favor the main branch when there are multiple candidates with the same distance
                mainBranchName
            } else bestCandidates.first()
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Couldn't find best matching branch", e)
            null
        }

        cacheByRepoPath[repoCacheKey] = Pair(cacheKey, result)
        return result
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
        val aheadCount = getNumberOfCommitsBetween(repository, mergeBase.asString(), from) ?: return null
        val behindCount = getNumberOfCommitsBetween(repository, mergeBase.asString(), to) ?: return null
        return aheadCount + behindCount
    }

    private fun getNumberOfCommitsBetween(
        repository: GitRepository,
        base: String,
        branchedOut: String,
    ): Int? {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.REV_LIST)
        handler.addParameters("--count", "$base..$branchedOut")
        handler.setSilent(true)
        return try {
            Integer.parseInt(Git.getInstance().runCommand(handler).getOutputOrThrow().trim())
        } catch (e: Exception) {
            throw Exception("Cannot get number of commits between '$base' and '$branchedOut'", e)
        }
    }
}
