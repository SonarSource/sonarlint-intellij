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

interface McpFileSystem {
    fun read(path: Path): ByteArray?

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
    private val writer: (Path, ByteArray) -> Unit = { path, content -> Files.write(path, content) }
) : McpFileSystem {
    override fun read(path: Path): ByteArray? = if (Files.exists(path)) Files.readAllBytes(path) else null

    override fun writeSiblingTemp(path: Path, content: ByteArray): Path {
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, "${path.fileName}.", ".tmp")
        try {
            writer(temp, content)
            return temp
        } catch (error: Throwable) {
            runCatching { Files.deleteIfExists(temp) }.onFailure(error::addSuppressed)
            throw error
        }
    }

    override fun replace(temp: Path, target: Path) {
        try {
            atomicMove(temp, target)
        } catch (_: AtomicMoveNotSupportedException) {
            fallbackMove(temp, target)
        }
    }

    override fun deleteIfExists(path: Path) {
        Files.deleteIfExists(path)
    }

    override fun isSymbolicLink(path: Path): Boolean = Files.isSymbolicLink(path)
}
