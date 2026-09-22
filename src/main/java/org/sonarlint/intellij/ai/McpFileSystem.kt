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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFilePermission
import java.nio.ByteBuffer
import java.util.UUID

interface McpFileSystem {
    fun read(path: Path): ByteArray?

    fun createBackup(path: Path, content: ByteArray): Path?

    fun writeSiblingTemp(path: Path, content: ByteArray): Path

    fun replace(temp: Path, target: Path)

    fun deleteIfExists(path: Path)

    fun isSymbolicLink(path: Path): Boolean
}

class NioMcpFileSystem(
    private val atomicMove: (Path, Path) -> Unit = { source, target ->
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    },
    private val fallbackMove: (Path, Path) -> Unit = { source, target ->
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    },
    private val ownerOnlyWriter: (Path, ByteArray) -> Unit = ::writeOwnerOnlyNewFile
) : McpFileSystem {
    override fun read(path: Path): ByteArray? = if (Files.exists(path)) Files.readAllBytes(path) else null

    override fun createBackup(path: Path, content: ByteArray): Path? {
        val backup = path.resolveSibling("${path.fileName}.bak")
        Files.createDirectories(path.parent)
        return try {
            writeNewFile(backup, content)
            backup
        } catch (_: FileAlreadyExistsException) {
            // An existing backup is intentionally preserved.
            null
        }
    }

    override fun writeSiblingTemp(path: Path, content: ByteArray): Path {
        Files.createDirectories(path.parent)
        val temp = path.resolveSibling("${path.fileName}.${UUID.randomUUID()}.tmp")
        writeNewFile(temp, content)
        return temp
    }

    override fun replace(temp: Path, target: Path) {
        try {
            atomicMove(temp, target)
        } catch (_: AtomicMoveNotSupportedException) {
            fallbackMove(temp, target)
        }
        setOwnerOnlyPermissions(target)
    }

    override fun deleteIfExists(path: Path) {
        Files.deleteIfExists(path)
    }

    override fun isSymbolicLink(path: Path): Boolean = Files.isSymbolicLink(path)

    private fun writeNewFile(path: Path, content: ByteArray) {
        try {
            ownerOnlyWriter(path, content)
        } catch (error: FileAlreadyExistsException) {
            throw error
        } catch (error: Throwable) {
            Files.deleteIfExists(path)
            throw error
        }
    }

    private fun setOwnerOnlyPermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY_PERMISSIONS)
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX file systems do not expose these permissions.
        }
    }

    companion object {
        private val OWNER_ONLY_PERMISSIONS = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

        private fun writeOwnerOnlyNewFile(path: Path, content: ByteArray) {
            val options = setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            val channel = if (Files.getFileStore(path.parent).supportsFileAttributeView("posix")) {
                Files.newByteChannel(path, options, PosixFilePermissions.asFileAttribute(OWNER_ONLY_PERMISSIONS))
            } else {
                Files.newByteChannel(path, options)
            }
            channel.use {
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    it.write(buffer)
                }
            }
        }
    }
}
