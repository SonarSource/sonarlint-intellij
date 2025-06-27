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
package org.sonarlint.intellij.analysis

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalFileExclusionsTest {

    @Test
    fun `should convert file path to glob pattern`(@TempDir tempDir: File) {
        val tempFile = tempDir.resolve("test.txt")
        val path = tempFile.absolutePath

        val result = LocalFileExclusions.toGlobPattern(path)

        assertThat(result).endsWith("**${tempFile.path}")
        assertThat(result).doesNotEndWith("/**")
    }

    @Test
    fun `should convert directory path to glob pattern`(@TempDir tempDir: File) {
        val path = tempDir.absolutePath

        val result = LocalFileExclusions.toGlobPattern(path)

        assertThat(result).endsWith("**${tempDir.path}/**")
    }

    @Test
    fun `should normalize backslashes and remove trailing slashes`() {
        val path = "foo\\bar\\baz////"

        val result = LocalFileExclusions.toGlobPattern(path)

        assertThat(result).contains("foo/bar/baz")
        assertThat(result).doesNotContain("//")
        assertThat(result).doesNotEndWith("/")
    }

    @Test
    fun `should add leading slash if missing`() {
        val path = "foo/bar"

        val result = LocalFileExclusions.toGlobPattern(path)

        assertThat(result).contains("**/foo/bar")
    }

}
