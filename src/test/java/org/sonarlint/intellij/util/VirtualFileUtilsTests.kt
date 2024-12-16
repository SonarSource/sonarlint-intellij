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
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.application
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.util.VirtualFileUtils.removeMarkdownCells

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

    @Test
    fun test_should_correctly_encode_basic_file() {
        val virtualFile = generateVirtualFileWithName("foo.java")

        val uri = VirtualFileUtils.toURI(virtualFile)
        val decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8)

        assertThat(uri).isNotNull()
        assertThat(uri.toString()).isEqualTo("file:///home/test/foo.java")
        assertThat(decodedUri).isEqualTo("file:///home/test/foo.java")
    }

    @Test
    fun test_should_correctly_encode_file_with_special_characters() {
        val virtualFile = generateVirtualFileWithName("{}[] |.java")

        val uri = VirtualFileUtils.toURI(virtualFile)
        val decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8)

        assertThat(uri).isNotNull()
        assertThat(uri.toString()).isEqualTo("file:///home/test/%7B%7D%5B%5D%20%7C.java")
        assertThat(decodedUri).isEqualTo("file:///home/test/{}[] |.java")
    }

    private fun generateVirtualFileWithName(fileName: String): VirtualFile {
        val virtualFile = mock(VirtualFile::class.java)
        `when`(virtualFile.isInLocalFileSystem).thenReturn(true)
        `when`(virtualFile.path).thenReturn("/home/test/$fileName")
        val fileSystem = mock(VirtualFileSystem::class.java)
        `when`(fileSystem.protocol).thenReturn("file")
        `when`(virtualFile.fileSystem).thenReturn(fileSystem)
        return virtualFile
    }

    @Test
    fun should_remove_markdown_cells_from_notebook() {
        val fileContent = """
            #%%
            t = 1
            #%% md
            test message
            #%%
            t = 2""".trimIndent()

        val result = removeMarkdownCells(fileContent)

        assertThat(result).isEqualTo(
            """
            #%%
            t = 1
            #%%
            t = 2""".trimIndent()
        )
    }

}
