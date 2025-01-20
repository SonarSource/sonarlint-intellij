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
package org.sonarlint.intellij.common.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.roots.GeneratedSourcesFilter.isGeneratedSourceByAnyFilter
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.ui.SonarLintConsole

class FileUtils {

    companion object {
        // To be used with iterateContent, because it already skips ignored and excluded files
        fun isFileValidForSonarLint(file: VirtualFile, project: Project): Boolean {
            try {
                val toSkip = computeReadActionSafely(file, project) {
                    (!ApplicationManager.getApplication().isUnitTestMode && !file.isDirectory && FileUtilRt.isTooLarge(file.length))
                        || FileElement.isArchive(file)
                        || ProjectCoreUtil.isProjectOrWorkspaceFile(file)
                        || isGeneratedSourceByAnyFilter(file, project)
                }

                return false == toSkip
            } catch (e: Exception) {
                SonarLintConsole.get(project).error("Error while visiting a file, reason: " + e.message)
                return false
            }
        }

        // To be used when using iterating over all children
        fun isFileValidForSonarLintWithExtensiveChecks(file: VirtualFile, project: Project): Boolean {
            try {
                val fileIndex = ProjectRootManager.getInstance(project).fileIndex
                val toSkip = computeReadActionSafely(file, project) {
                    (!ApplicationManager.getApplication().isUnitTestMode && !file.isDirectory && FileUtilRt.isTooLarge(file.length))
                        || FileElement.isArchive(file)
                        || !fileIndex.isInContent(file)
                        || fileIndex.isInLibrarySource(file)
                        || ProjectCoreUtil.isProjectOrWorkspaceFile(file)
                        || isGeneratedSourceByAnyFilter(file, project)
                }

                return false == toSkip
            } catch (e: Exception) {
                SonarLintConsole.get(project).error("Error while visiting a file, reason: " + e.message)
                return false
            }
        }
    }

}
