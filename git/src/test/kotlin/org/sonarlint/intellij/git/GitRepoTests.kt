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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.project.stateStore
import com.intellij.testFramework.replaceService
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

private const val MAIN_BRANCH = "main"
private const val EXPECTED = "expected-branch"

class GitRepoTests : AbstractLightTests() {

    lateinit var vcsManager: ProjectLevelVcsManagerImpl
    lateinit var root: Path

    @BeforeEach
    override fun setUp() {
        vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
        vcsManager.waitForInitialized()
        root = project.stateStore.projectBasePath
        // replace to avoid flaky test behaviour, otherwise it can mess up verify counts.
        project.replaceService(GitBranchIncomingOutgoingManager::class.java,
            mock(GitBranchIncomingOutgoingManager::class.java),
            testRootDisposable)
    }

    @AfterEach
    override fun tearDown() {
        vcsManager.unregisterVcs(GitVcs.getInstance(project))
        Files.list(root).forEach { child -> child.toFile().deleteRecursively() }
    }

    @Test
    fun `should return null for empty repo`() {
        val gitRepository = initRepo()
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            gitRepository.update()
        }.get()
        val tested = GitRepo(gitRepository, project)

        val result = tested.electBestMatchingServerBranchForCurrentHead(MAIN_BRANCH, setOf())

        assertThat(result).isNull()
    }

    @Test
    fun `should return current branch if it's known`() {
        val gitRepository = initRepoWithCommit()
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            gitRepository.update()
        }.get()
        val tested = GitRepo(gitRepository, this.project)

        val result = tested.electBestMatchingServerBranchForCurrentHead(MAIN_BRANCH, setOf(MAIN_BRANCH))

        assertThat(result).isEqualTo(MAIN_BRANCH)
    }

    @Test
    fun `should return current non-main branch if it's known`() {
        val gitRepository = initRepoWithCommit()
        newBranch(EXPECTED)
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            gitRepository.update()
        }.get()
        val tested = GitRepo(gitRepository, this.project)

        val result = tested.electBestMatchingServerBranchForCurrentHead(MAIN_BRANCH, setOf(MAIN_BRANCH, EXPECTED))

        assertThat(result).isEqualTo(EXPECTED)
    }

    @Test
    fun `should return null when no remote branches match local ones`() {
        val gitRepository = initRepoWithCommit()
        newBranch("branch-1")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            gitRepository.update()
        }.get()
        val tested = GitRepo(gitRepository, this.project)

        val result = tested.electBestMatchingServerBranchForCurrentHead("otherMain", setOf("otherMain", "otherBranch-1"))

        assertThat(result).isNull()
    }

    @Test
    fun `should select closest of non-main, non-current branch names`() {
        val gitRepository = initRepoWithCommit()
        newBranch("branch-1")
        commitNewFile("1.txt")
        newBranch(EXPECTED)
        commitNewFile("2.txt")
        newBranch("branch-3")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            gitRepository.update()
        }.get()
        val tested = GitRepo(gitRepository, this.project)

        val result = tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED)
        )

        assertThat(result).isEqualTo(EXPECTED)
    }

    @Test
    fun `should select main when it is one of the closest`() {
        val gitRepository = initRepoWithCommit()
        newBranch("branch-1")
        newBranch("branch-2")
        newBranch("local")
        commitNewFile("1.txt")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            gitRepository.update()
        }.get()
        val tested = GitRepo(gitRepository, this.project)

        val result = tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", "branch-2")
        )

        assertThat(result).isEqualTo(MAIN_BRANCH)
    }

    @Test
    fun `should use cache instead of trying to iterate all branches`() {
        val repoSpy = spy(initRepoWithCommit())
        newBranch("branch-1")
        commitNewFile("1.txt")
        newBranch(EXPECTED)
        commitNewFile("2.txt")
        newBranch("branch-3")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            repoSpy.update()
        }.get()
        val tested = GitRepo(repoSpy, this.project)

        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))
        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))
        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))

        // always invoked
        verify(repoSpy, times(3)).currentBranchName
        verify(repoSpy, times(3)).currentRevision
        // invoked when no cache
        verify(repoSpy, times(1)).branches
    }

    @Test
    fun `should not use cache when currentBranch is different`() {
        val repoSpy = spy(initRepoWithCommit())
        newBranch("branch-1")
        commitNewFile("1.txt")
        newBranch(EXPECTED)
        commitNewFile("2.txt")
        newBranch("branch-3")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            repoSpy.update()
        }.get()
        val tested = GitRepo(repoSpy, this.project)

        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))
        newBranch("new-branch")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            repoSpy.update()
        }.get()
        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))

        // always invoked
        verify(repoSpy, times(2)).currentBranchName
        verify(repoSpy, times(2)).currentRevision
        // invoked when no cache
        verify(repoSpy, times(2)).branches
    }

    @Test
    fun `should not use cache when mainBranchName is different`() {
        val repoSpy = spy(initRepoWithCommit())
        newBranch("branch-1")
        commitNewFile("1.txt")
        newBranch(EXPECTED)
        commitNewFile("2.txt")
        newBranch("branch-3")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            repoSpy.update()
        }.get()
        val tested = GitRepo(repoSpy, this.project)

        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))
        tested.electBestMatchingServerBranchForCurrentHead(
            "branch-1",
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))

        // always invoked
        verify(repoSpy, times(2)).currentBranchName
        verify(repoSpy, times(2)).currentRevision
        // invoked when no cache
        verify(repoSpy, times(2)).branches
    }

    @Test
    fun `should not use cache when allBranchNames is different`() {
        val repoSpy = spy(initRepoWithCommit())
        newBranch("branch-1")
        commitNewFile("1.txt")
        newBranch(EXPECTED)
        commitNewFile("2.txt")
        newBranch("branch-3")
        ApplicationManager.getApplication().executeOnPooledThread<Unit> {
            repoSpy.update()
        }.get()
        val tested = GitRepo(repoSpy, this.project)

        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED))
        tested.electBestMatchingServerBranchForCurrentHead(
            MAIN_BRANCH,
            setOf(MAIN_BRANCH, "branch-1", EXPECTED, "branch-other"))

        // always invoked
        verify(repoSpy, times(2)).currentBranchName
        verify(repoSpy, times(2)).currentRevision
        // invoked when no cache
        verify(repoSpy, times(2)).branches
    }

    private fun newGitRepository(): GitRepository {
        vcsManager.setDirectoryMapping(root.toString(), GitVcs.NAME)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)
        return ApplicationManager.getApplication().executeOnPooledThread<GitRepository> {
            GitUtil.getRepositoryManager(this.project).getRepositoryForRoot(file)!!
        }.get()
    }

    private fun git(command: GitCommand, vararg params: String): String {
        val handler = GitLineHandler(
            project,
            root.toFile(),
            command
        )
        handler.addParameters(*params)
        return Git.getInstance().runCommand(handler).getOutputOrThrow()
    }

    private fun initRepo(): GitRepository {
        Files.createDirectories(root)
        git(GitCommand.INIT, "--initial-branch=main")
        git(GitCommand.CONFIG, "user.name", "Test User")
        git(GitCommand.CONFIG, "user.email", "test@user.com")
        return newGitRepository()
    }

    private fun initRepoWithCommit(): GitRepository {
        val gitRepository = initRepo()
        commitNewFile()
        return gitRepository
    }

    private fun commitNewFile(fileName: String = "init.txt") {
        val commitFile = File(root.toFile(), fileName)
        commitFile.createNewFile()
        git(GitCommand.ADD, fileName)
        git(GitCommand.COMMIT, "-m", "commit $fileName")
    }

    private fun newBranch(branchName: String) {
        git(GitCommand.CHECKOUT, "-b", branchName)
    }
}
