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
package org.sonarlint.intellij.sharing

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.exists
import java.nio.file.Path
import java.nio.file.Paths
import org.sonarlint.intellij.SonarLintIntelliJClient.toClientFileDto
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.util.SonarLintUtils.isRider
import org.sonarlint.intellij.common.vcs.VcsRepoProvider
import org.sonarlint.intellij.util.SonarLintAppUtils.getRelativePathForAnalysis
import org.sonarlint.intellij.util.computeOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

class SharedConnectedModeUtils {

    companion object {

        private const val SONARLINT_FOLDER = ".sonarlint"
        private const val CONNECTED_MODE_FILE = "connectedMode.json"

        fun findSharedFolder(project: Project): Path? {
            return computeOnPooledThread(project, "Find Shared Folder Task") {
                var root = project.basePath?.let { Paths.get(it) }
                var sonarlintFolder = root?.resolve(SONARLINT_FOLDER)

                if (isRider() && (sonarlintFolder == null || !sonarlintFolder.exists())) {
                    root = findSharedFolderForRider(project)
                    sonarlintFolder = root?.resolve(SONARLINT_FOLDER) ?: sonarlintFolder
                }

                sonarlintFolder
            }
        }

        fun findSharedFolder(module: Module): Path? {
            return computeOnPooledThread(module.project, "Find Module Shared Folder Task") {
                val contentRoots = ModuleRootManager.getInstance(module).contentRoots
                // Only handle basic structure of modules with a single root
                if (contentRoots.size == 1) {
                    contentRoots.firstOrNull()?.path?.let { Paths.get(it).resolve(SONARLINT_FOLDER) }
                } else {
                    getService(
                        module.project,
                        SonarLintConsole::class.java
                    ).debug("Could not share binding for module '$module' because of its complex structure. Try creating the file manually.")
                    null
                }
            }
        }

        private fun findSharedFolderForRider(project: Project): Path? {
            val repositoriesEPs = VcsRepoProvider.EP_NAME.extensionList
            // Only one solution can be opened at a time in Rider
            return project.modules.mapNotNull { module ->
                val repositories = repositoriesEPs.mapNotNull {
                    it.getRepoFor(module)
                }.toSet()
                if (repositories.isEmpty()) {
                    return@mapNotNull null
                }
                if (repositories.size > 1) {
                    getService(
                        module.project,
                        SonarLintConsole::class.java
                    ).debug("Several candidate VCS repositories detected for module '$module', choosing first")
                }
                repositories.first()
            }.toSet().firstOrNull()?.getGitDir()
        }

        fun findConnectedModeFile(sonarlintFolder: Path, project: Project, configScopeId: String): ClientFileDto? {
            VfsUtil.findFile(sonarlintFolder, false)?.let { sonarlintDir ->
                sonarlintDir.children.forEach { conf ->
                    if (isRider()) {
                        checkForRiderConnectedModeFile(conf, project, configScopeId)?.let { return it }
                    } else {
                        if (conf.name == CONNECTED_MODE_FILE) {
                            getRelativePathForAnalysis(project, conf)?.let { path ->
                                return toClientFileDto(project, configScopeId, conf, path, Language.JSON)
                            }
                        }
                    }
                }
            }
            return null
        }

        private fun checkForRiderConnectedModeFile(file: VirtualFile, project: Project, configScopeId: String): ClientFileDto? {
            val solutionName = file.nameWithoutExtension
            if (project.name == solutionName) {
                getRelativePathForAnalysis(project.modules[0], file)?.let { path ->
                    toClientFileDto(project, configScopeId, file, path, Language.JSON)?.let { return it }
                }
            }
            return null
        }

    }

}
