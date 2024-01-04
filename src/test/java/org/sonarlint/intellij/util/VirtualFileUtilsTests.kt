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
package org.sonarlint.intellij.util

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintHeavyTests

class VirtualFileUtilsTests : AbstractSonarLintHeavyTests() {
    /** SLI-942: Don't analyze binary files */
    @Test
    fun test_isNonBinaryFile() {
        lateinit var directory: VirtualFile
        lateinit var binaryFile: VirtualFile
        lateinit var nonBinaryFile: VirtualFile

        val module = createModule("SLI-942")
        val contentRoot = createTestProjectStructure()
        ModuleRootModificationUtil.addContentRoot(module, contentRoot)

        application.runWriteAction {
            directory = contentRoot.createChildDirectory(project, "src")

            // HowTo Binary file to test: add values outside text character spectrum
            binaryFile = directory.createChildData(project, "Binary.kt")
            binaryFile.setBinaryContent(
                intArrayOf(0x01, 0xFF).foldIndexed(ByteArray(2)) { i, a, v -> a.apply { set(i, v.toByte()) } }
            )

            nonBinaryFile = directory.createChildData(project, "NonBinary.kt")
            nonBinaryFile.setBinaryContent("fun main() { println('main method') }".toByteArray())
        }

        assertThat(VirtualFileUtils.isNonBinaryFile(directory)).isFalse()
        assertThat(VirtualFileUtils.isNonBinaryFile(binaryFile)).isFalse()
        assertThat(VirtualFileUtils.isNonBinaryFile(nonBinaryFile)).isTrue()
    }
}
