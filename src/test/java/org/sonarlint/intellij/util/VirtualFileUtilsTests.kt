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
package org.sonarlint.intellij.util

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.application
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.util.VirtualFileUtils.removeMarkdownCells

class VirtualFileUtilsTests : AbstractSonarLintHeavyTests() {

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

    @Test
    fun test_should_correctly_encode_file_with_special_characters_in_path() {
        val virtualFile = mock(VirtualFile::class.java)
        `when`(virtualFile.isInLocalFileSystem).thenReturn(true)
        `when`(virtualFile.path).thenReturn("/home/test/中文字符/{}[] |.java")
        val fileSystem = mock(VirtualFileSystem::class.java)
        `when`(fileSystem.protocol).thenReturn("file")
        `when`(virtualFile.fileSystem).thenReturn(fileSystem)

        val uri = VirtualFileUtils.toURI(virtualFile)
        val decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8)

        assertThat(uri).isNotNull()
        assertThat(uri.toString()).isEqualTo("file:///home/test/%E4%B8%AD%E6%96%87%E5%AD%97%E7%AC%A6/%7B%7D%5B%5D%20%7C.java")
        assertThat(decodedUri).isEqualTo("file:///home/test/中文字符/{}[] |.java")
    }

    @Test
    fun should_remove_markdown_cells_from_notebook() {
        val fileContent = """
            #%%
            t = 1
            #%% md
            test message
            #%%
            t = 2
            #%% raw
            raw""".trimIndent()

        val result = removeMarkdownCells(fileContent)

        assertThat(result).isEqualTo(
            """
            #%%
            t = 1
            #%%
            t = 2""".trimIndent()
        )
    }

    @Test
    fun test_should_correctly_encode_windows_path() {
        val virtualFile = mock(VirtualFile::class.java)
        `when`(virtualFile.isInLocalFileSystem).thenReturn(true)
        `when`(virtualFile.path).thenReturn("C:/Users/test/中文字符/file.java")
        val fileSystem = mock(VirtualFileSystem::class.java)
        `when`(fileSystem.protocol).thenReturn("file")
        `when`(virtualFile.fileSystem).thenReturn(fileSystem)

        val uri = VirtualFileUtils.toURI(virtualFile)
        val decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8)

        assertThat(uri).isNotNull()
        assertThat(uri.toString()).isEqualTo("file:///C:/Users/test/%E4%B8%AD%E6%96%87%E5%AD%97%E7%AC%A6/file.java")
        assertThat(decodedUri).isEqualTo("file:///C:/Users/test/中文字符/file.java")
    }

    @Test
    fun test_getEncoding_when_virtual_file_has_charset_set() {
        val virtualFile = mock(VirtualFile::class.java)
        val mockCharset = mock(Charset::class.java)
        `when`(virtualFile.isCharsetSet).thenReturn(true)
        `when`(virtualFile.charset).thenReturn(mockCharset)
        `when`(mockCharset.name()).thenReturn("UTF-8")

        val result = VirtualFileUtils.getEncoding(virtualFile, project)

        assertThat(result).isEqualTo("UTF-8")
    }

    @Test
    fun test_getEncoding_when_virtual_file_no_charset_but_project_manager_returns_encoding() {
        val virtualFile = mock(VirtualFile::class.java)
        `when`(virtualFile.isCharsetSet).thenReturn(false)
        val testFile = createTestFile("test.txt", "test content")
        
        val result = VirtualFileUtils.getEncoding(testFile, project)

        // The result should be the default charset name since the file doesn't have a charset set
        // and the project manager will return null
        assertThat(result).isEqualTo(Charset.defaultCharset().name())
    }

    private fun createTestFile(fileName: String, content: String): VirtualFile {
        val module = createModule("test-module")
        val contentRoot = createTestProjectStructure()
        ModuleRootModificationUtil.addContentRoot(module, contentRoot)
        
        lateinit var testFile: VirtualFile
        application.runWriteAction {
            testFile = contentRoot.createChildData(project, fileName)
            testFile.setBinaryContent(content.toByteArray())
        }
        return testFile
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

}
