/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.sonar.api.batch.fs.InputFile
import org.sonarlint.intellij.analysis.SonarLintAnalyzer
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem
import java.util.stream.Stream

internal class ModuleFileSystem(private val project: Project, private val module: Module) : ClientModuleFileSystem {
    override fun files(language: String, type: InputFile.Type): Stream<ClientInputFile> {
        return files()
            .filter { f -> f.relativePath().endsWith(language) }
            .filter { f -> f.isTest == (type == InputFile.Type.TEST) }
    }

    override fun files(): Stream<ClientInputFile> {
        val files: MutableList<ClientInputFile> = ArrayList()
        val sonarLintAnalyzer = project.getService(SonarLintAnalyzer::class.java)
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(project, {
            ModuleRootManager.getInstance(module).fileIndex.iterateContent { fileOrDir: VirtualFile ->
                ProgressManager.checkCanceled()
                if (!fileOrDir.isDirectory && !ProjectCoreUtil.isProjectOrWorkspaceFile(fileOrDir, fileOrDir.fileType)) {
                    val element = sonarLintAnalyzer.createClientInputFile(module, fileOrDir, null)
                    if (element != null) {
                        files.add(element)
                    }
                }
                true
            }
        })
        return files.stream()
    }
}
