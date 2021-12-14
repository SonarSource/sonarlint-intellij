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
package org.sonarlint.intellij.fs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent
import java.nio.charset.Charset

fun buildModuleFileEvent(
    module: Module,
    file: VirtualFile,
    type: ModuleFileEvent.Type
): ClientModuleFileEvent? {
    val relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, file) ?: return null
    return ClientModuleFileEvent.of(
        DefaultClientInputFile(
            file,
            relativePath,
            TestSourcesFilter.isTestSources(file, module.project),
            getEncoding(module.project, file)
        ),
        type
    )
}

fun buildModuleFileEvent(
  module: Module,
  file: VirtualFile,
  document: Document,
  type: ModuleFileEvent.Type
): ClientModuleFileEvent? {
    val relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, file) ?: return null
    return ClientModuleFileEvent.of(
      DefaultClientInputFile(
        file,
        relativePath,
        TestSourcesFilter.isTestSources(file, module.project),
        getEncoding(module.project, file),
        document.text,
        document.modificationStamp,
        null
      ),
      type
    )
}

private fun getEncoding(project: Project, f: VirtualFile): Charset {
    val encodingProjectManager = EncodingProjectManager.getInstance(project)
    val encoding = encodingProjectManager.getEncoding(f, true)
    return encoding ?: Charset.defaultCharset()
}
