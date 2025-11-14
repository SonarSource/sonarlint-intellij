/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.rider

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.ProjectModelEntityVisitor
import com.jetbrains.rider.projectView.workspace.getVirtualFileAsContentRoot
import com.jetbrains.rider.projectView.workspace.isProjectFile
import org.sonarlint.intellij.common.analysis.FilesContributor
import org.sonarlint.intellij.common.util.FileUtils.isFileValidForSonarLintWithExtensiveChecks

class RiderFilesContributor : FilesContributor {

    override fun listFiles(module: Module): MutableSet<VirtualFile> {
        val filesInContentRoots = mutableSetOf<VirtualFile>()

        // List files in Solution
        filesInContentRoots.addAll(listFilesInSolution(module))

        return filesInContentRoots
    }

    private fun listFilesInSolution(module: Module): Set<VirtualFile> {
        val filesInSolution = mutableSetOf<VirtualFile>()
        val visitor = object : ProjectModelEntityVisitor() {
            override fun visitProjectFile(entity: ProjectModelEntity): Result {
                if (module.isDisposed) {
                    return Result.Stop
                }

                if (entity.isProjectFile()) {
                    entity.getVirtualFileAsContentRoot()?.let {
                        if (!it.isDirectory && isFileValidForSonarLintWithExtensiveChecks(it, module.project)) {
                            filesInSolution.add(it)
                        }
                    }
                }

                return Result.Continue
            }
        }
        visitor.visit(module.project)
        return filesInSolution
    }

}
