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

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.testFramework.PsiTestUtil
import git4idea.GitVcs
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.sonarlint.intellij.AbstractSonarLintHeavyTest
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches
import java.nio.file.Paths
import java.util.Optional

internal class VcsServiceTest : AbstractSonarLintHeavyTest() {

    private val connectedEngine = mock(ConnectedSonarLintEngine::class.java)
    private lateinit var vcsService: VcsService

    override fun setUp() {
        super.setUp()
        vcsService = VcsService(project)
    }

    @Test
    fun test_should_not_resolve_server_branch_when_project_is_not_bound() {
        val resolvedBranchName = vcsService.resolveServerBranchName(module)

        assertThat(resolvedBranchName).isNull()
    }

    @Test
    fun test_should_not_resolve_server_branch_when_project_is_not_a_git_repo() {
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        `when`(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                Optional.of("master")
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        val resolvedBranchName = vcsService.resolveServerBranchName(module)

        assertThat(resolvedBranchName).isNull()
    }

    @Test
    fun test_should_not_resolve_server_branch_when_module_have_different_git_repo_as_content_roots() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module, "contentRoot1")
        addContentRootWithGitRepo(module, "contentRoot2")
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        `when`(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                Optional.of("master")
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        val resolvedBranchName = vcsService.resolveServerBranchName(module)

        assertThat(resolvedBranchName).isNull()
    }

    @Test
    fun test_should_resolve_exact_server_branch_when_module_is_bound_and_current_branch_is_known_on_server() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        `when`(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master", "branch1"),
                Optional.of("master")
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        val resolvedBranchName = vcsService.resolveServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("branch1")
    }

    @Test
    fun test_should_resolve_closest_server_branch_when_module_is_bound_and_current_branch_unknown_on_server() {
        val module = createModule("aModule")
        addContentRootWithGitRepo(module)
        connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey")
        connectModuleTo(module, "moduleKey")
        `when`(connectedEngine.getServerBranches("moduleKey")).thenReturn(
            ProjectBranches(
                setOf("master"),
                Optional.of("master")
            )
        )
        getEngineManager().registerEngine(connectedEngine, "connection")

        // current branch is 'branch1'
        val resolvedBranchName = vcsService.resolveServerBranchName(module)

        assertThat(resolvedBranchName).isEqualTo("master")
    }

    private fun addContentRootWithGitRepo(module: Module, contentRootPath: String = "path") {
        val contentRootBasePath = Paths.get(ModuleUtil.getModuleDirPath(module)).resolve(contentRootPath).toFile()
        // this .git folder contains a repo with 2 branches: master and branch1 (current)
        FileUtil.copyDir(getTestDataPath().resolve("git/").toFile(), contentRootBasePath.resolve(".git/"))
        val rootVf = refreshAndFindFile(contentRootBasePath)
        refreshRecursively(rootVf)
        val rootPath = rootVf.path
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        vcsManager.directoryMappings = vcsManager.directoryMappings + listOf(VcsDirectoryMapping(rootPath, GitVcs.NAME))
        PsiTestUtil.addContentRoot(module, rootVf)
    }
}
