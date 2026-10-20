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
package org.sonarlint.intellij.fixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Files
import java.nio.file.Path
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

object LightTestProjectFiles {

    fun registerFromTestData(
        module: Module,
        testDataPath: String,
        relativePath: String,
        createTestPsiFile: (relativePath: String, content: String) -> PsiFile,
    ): VirtualFile {
        val sourcePath = Path.of(testDataPath, relativePath).toAbsolutePath()
        check(Files.isRegularFile(sourcePath)) { "Test file not found: $sourcePath" }
        val content = Files.readString(sourcePath)
        val virtualFile = createTestPsiFile(relativePath, content).virtualFile
        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile))),
            true,
        )
        return virtualFile
    }
}
