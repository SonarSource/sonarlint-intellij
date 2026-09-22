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
package org.sonarlint.intellij.ai

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class McpFileSystemTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `backup never overwrites and temp replacement uses owner-only permissions`() {
        val fileSystem = NioMcpFileSystem()
        val target = tempDir.resolve("mcp.json")
        Files.writeString(target, "original")

        fileSystem.createBackup(target, "original".toByteArray())
        fileSystem.createBackup(target, "new snapshot".toByteArray())
        val temp = fileSystem.writeSiblingTemp(target, "updated".toByteArray())
        if (Files.getFileStore(temp).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(temp)).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
            assertThat(Files.getPosixFilePermissions(tempDir.resolve("mcp.json.bak"))).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
        }
        fileSystem.replace(temp, target)

        assertThat(Files.readString(tempDir.resolve("mcp.json.bak"))).isEqualTo("original")
        assertThat(Files.readString(target)).isEqualTo("updated")
        assertThat(Files.exists(temp)).isFalse()
        if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(target)).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
            assertThat(Files.getPosixFilePermissions(tempDir.resolve("mcp.json.bak"))).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
        }
    }

    @Test
    fun `falls back when atomic replacement is unsupported`() {
        var fallbackUsed = false
        val fileSystem = NioMcpFileSystem(
            { source, target -> throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "unsupported") },
            { source, target ->
                fallbackUsed = true
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        )
        val target = tempDir.resolve("mcp.json")
        val temp = fileSystem.writeSiblingTemp(target, "updated".toByteArray())

        fileSystem.replace(temp, target)

        assertThat(fallbackUsed).isTrue()
        assertThat(Files.readString(target)).isEqualTo("updated")
    }

    @Test
    fun `removes a partially created secret file when owner-only creation fails`() {
        var partial: Path? = null
        val fileSystem = NioMcpFileSystem(ownerOnlyWriter = { path, _ ->
            partial = path
            Files.writeString(path, "partial", StandardOpenOption.CREATE_NEW)
            throw IllegalStateException("permission failure")
        })

        org.assertj.core.api.Assertions.assertThatThrownBy {
            fileSystem.writeSiblingTemp(tempDir.resolve("mcp.json"), "secret".toByteArray())
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(partial).isNotNull()
        assertThat(Files.exists(partial!!)).isFalse()
    }
}
