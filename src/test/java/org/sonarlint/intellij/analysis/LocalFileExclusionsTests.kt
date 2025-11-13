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
package org.sonarlint.intellij.analysis

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LocalFileExclusionsTest {

    @Test
    fun `should convert file path to glob pattern`(@TempDir tempDir: File) {
        val tempFile = tempDir.resolve("test.txt")
        val path = tempFile.absolutePath
        val normalizedPath = tempFile.path.replace('\\', '/')
        val expected = if (normalizedPath.startsWith("/")) {
            "**$normalizedPath"
        } else {
            "**/$normalizedPath"
        }

        val result = LocalFileExclusions.fileToGlobPattern(path)

        assertThat(result).isEqualTo(expected)
        assertThat(result).doesNotEndWith("/**")
    }

    @Test
    fun `should convert directory path to glob pattern`(@TempDir tempDir: File) {
        val path = tempDir.absolutePath
        val normalizedPath = tempDir.path.replace('\\', '/')
        val expected = if (normalizedPath.startsWith("/")) {
            "**$normalizedPath/**"
        } else {
            "**/$normalizedPath/**"
        }

        val result = LocalFileExclusions.directoryToGlobPattern(path)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `should normalize backslashes and remove trailing slashes`() {
        val path = "foo\\bar\\baz////"

        val result = LocalFileExclusions.directoryToGlobPattern(path)

        assertThat(result).contains("foo/bar/baz")
        assertThat(result).doesNotContain("//")
        assertThat(result).doesNotEndWith("/")
    }

    @Test
    fun `should add leading slash if missing`() {
        val path = "foo/bar"

        val result = LocalFileExclusions.directoryToGlobPattern(path)

        assertThat(result).contains("**/foo/bar")
    }

    @Test
    fun `should handle empty string as file`() {
        val result = LocalFileExclusions.fileToGlobPattern("")

        assertThat(result).isEqualTo("**/")
    }

    @Test
    fun `should treat non-existent path as file`() {
        val path = "nonexistent/path/to/file.txt"

        val result = LocalFileExclusions.fileToGlobPattern(path)

        assertThat(result).contains("**/nonexistent/path/to/file.txt")
        assertThat(result).doesNotEndWith("/**")
    }

    @Test
    fun `should handle mixed slashes and dot segments`() {
        val path = "foo/bar\\baz/./qux/../file.txt////"

        val result = LocalFileExclusions.fileToGlobPattern(path)

        assertThat(result).contains("foo/bar/baz/./qux/../file.txt")
        assertThat(result).doesNotContain("//")
        assertThat(result).doesNotEndWith("/")
    }

    @ParameterizedTest
    @CsvSource(
        "foo/bar, **/foo/bar",
        "/foo/bar, **/foo/bar",
        "foo/bar/, **/foo/bar",
        "foo/bar//, **/foo/bar",
        "foo\\bar, **/foo/bar"
    )
    fun `should normalize various file path formats`(input: String, expected: String) {
        val result = LocalFileExclusions.fileToGlobPattern(input)

        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "foo/bar, **/foo/bar/**",
        "/foo/bar, **/foo/bar/**",
        "foo/bar/, **/foo/bar/**",
        "foo/bar//, **/foo/bar/**",
        "foo\\bar, **/foo/bar/**"
    )
    fun `should normalize various dir path formats`(input: String, expected: String) {
        val result = LocalFileExclusions.directoryToGlobPattern(input)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `should handle already normalized path`() {
        val path = "/foo/bar"

        val result = LocalFileExclusions.fileToGlobPattern(path)

        assertThat(result).isEqualTo("**/foo/bar")
    }

    @Test
    fun `should handle relative path without leading slash`() {
        val path = "foo/bar/baz"

        val result = LocalFileExclusions.fileToGlobPattern(path)

        assertThat(result).contains("**/foo/bar/baz")
    }

    @Test
    fun `should handle relative path with leading slash`() {
        val path = "/foo/bar/baz"

        val result = LocalFileExclusions.fileToGlobPattern(path)

        assertThat(result).contains("**/foo/bar/baz")
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test
    fun `should handle Windows absolute path as file`() {
        val path = "C:\\foo\\bar\\baz.txt"

        val result = LocalFileExclusions.fileToGlobPattern(path)

        assertThat(result).contains("**/C:/foo/bar/baz.txt")
        assertThat(result).doesNotEndWith("/**")
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test
    fun `should handle Windows absolute path as directory`(@TempDir tempDir: File) {
        // Simulate a Windows directory path
        val dir = tempDir.resolve("winDir")
        dir.mkdirs()
        val path = dir.absolutePath.replace("/", "\\")

        val result = LocalFileExclusions.directoryToGlobPattern(path)

        assertThat(result).endsWith("/**")
    }

}
