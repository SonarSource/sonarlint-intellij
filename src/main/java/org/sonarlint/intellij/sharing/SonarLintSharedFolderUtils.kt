/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.util.io.exists
import java.nio.file.Path
import java.nio.file.Paths
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.util.SonarLintUtils.isRider
import org.sonarlint.intellij.common.vcs.ModuleVcsRepoProvider

class SonarLintSharedFolderUtils {

    companion object {

        fun findSharedFolder(project: Project): Path? {
            var root = project.basePath?.let { Paths.get(it) }
            var sonarlintFolder = root?.resolve(".sonarlint")

            if (isRider() && (sonarlintFolder == null || !sonarlintFolder.exists())) {
                root = findSharedFolderForRider(project)
                sonarlintFolder = root?.resolve(".sonarlint")
            }

            return sonarlintFolder
        }

        private fun findSharedFolderForRider(project: Project): Path? {
            val repositoriesEPs = ModuleVcsRepoProvider.EP_NAME.extensionList
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
                    ).debug("Several candidate Vcs repositories detected for module $module, choosing first")
                }
                repositories.first()
            }.toSet().firstOrNull()?.getGitDir()
        }

    }

}