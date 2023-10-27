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
package org.sonarlint.intellij.vcs

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.testFramework.PsiTestUtil
import git4idea.GitVcs
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.common.vcs.VcsService
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.fixtures.newSonarQubeConnection
import org.sonarlint.intellij.messages.PROJECT_BINDING_TOPIC
import org.sonarlint.intellij.messages.SERVER_BRANCHES_TOPIC
import org.sonarlint.intellij.util.ImmediateExecutorService
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException

internal class DefaultVcsServiceTests : AbstractSonarLintHeavyTests() {

    private lateinit var connectedEngine: ConnectedSonarLintEngine
    private lateinit var vcsService: DefaultVcsService

    @BeforeEach
    fun prepare() {
        connectedEngine = mock(ConnectedSonarLintEngine::class.java)
        vcsService = DefaultVcsService(project, ImmediateExecutorService())
        replaceProjectService(VcsService::class.java, vcsService)
    }

    @Test
    fun test_should_not_resolve_server_branch_when_project_is_not_bound() {
        assertThat(vcsService.getServerBranchName(module)).isNull()
    }

    @Test
    fun test_should_not_resolve_server_branch_when_project_is_not_a_git_repo() {
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")
    }

    @Test
    fun test_should_not_resolve_server_branch_when_storage_does_not_exist() {
        whenever(connectedEngine.getServerBranches("moduleKey")).thenThrow(StorageException("boom"))

        val resolvedServerBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedServerBranchName).isNull()
    }

    @Test
    fun test_should_resolve_server_branch_from_first_repo_when_module_have_different_git_repo_as_content_roots() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module, "contentRoot1")
        addContentRootWithGitRepo(module, "contentRoot2")
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("master")
    }

    @Test
    fun test_should_resolve_exact_server_branch_when_module_is_bound_and_current_branch_is_known_on_server() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("branch1")
    }

    @Test
    fun test_should_return_cached_value_when_already_resolved() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")

        whenever(connectedEngine.getServerBranches("moduleKey")).thenThrow(IllegalStateException("Should not be called because value is cached"))

        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")
    }

    @Test
    fun test_should_resolve_closest_server_branch() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        connectModuleTo(module, "moduleKey")
        whenever(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        // current branch is 'branch1'
        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("master")
    }

    @Test
    fun test_should_clear_cache_when_project_is_unbound() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")

        unbindProject()

        assertThat(vcsService.getServerBranchName(module)).isNull()
    }

    @Test
    fun test_should_refresh_cache_when_project_is_bound() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        assertThat(vcsService.getServerBranchName(module)).isNull()

        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        project.messageBus.syncPublisher(PROJECT_BINDING_TOPIC)
            .bindingChanged(null, ProjectBinding("connection", "projectKey", emptyMap()))

        assertThat(vcsService.getServerBranchName(module)).isEqualTo("branch1")
    }

    @Test
    fun test_should_refresh_cache_when_server_branches_are_updated() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")

        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                "master"
            )
        )
        project.messageBus.syncPublisher(SERVER_BRANCHES_TOPIC)
            .serverBranchesUpdated()

        val resolvedBranchName = vcsService.getServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("branch1")
    }

    @Test
    fun test_should_remove_disposed_module_from_cache_when_refreshing() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        whenever(connectedEngine.getServerBranches("projectKey")).thenReturn(
            ProjectBranches(
                setOf("master"),
                "master"
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")
        connectProjectTo(newSonarQubeConnection("connection"), "projectKey")
        assertThat(vcsService.getServerBranchName(module)).isEqualTo("master")
        ModuleManager.getInstance(project).disposeModule(module)

        vcsService.refreshCacheAsync()

        assertThat(vcsService.resolvedBranchPerModule).doesNotContainKey(module)
    }

    private fun addContentRootWithGitRepo(module: Module, contentRootPath: String = "path") {
        val contentRootBasePath = Paths.get(ModuleUtil.getModuleDirPath(module)).resolve(contentRootPath).toFile()
        // this .git folder contains a repo with 2 branches: master and branch1 (current)
        FileUtil.copyDir(getTestDataPath().resolve("git/").toFile(), contentRootBasePath.resolve(".git/"))
        val rootVf = refreshAndFindFile(contentRootBasePath)
        val rootPath = rootVf.path
        PsiTestUtil.addContentRoot(module, rootVf)
        val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
        vcsManager.directoryMappings = vcsManager.directoryMappings + listOf(VcsDirectoryMapping(rootPath, GitVcs.NAME))
        vcsManager.waitForInitialized()
    }
}
