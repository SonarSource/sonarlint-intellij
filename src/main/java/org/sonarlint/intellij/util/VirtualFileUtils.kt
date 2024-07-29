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

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

object VirtualFileUtils {

    fun toURI(file: VirtualFile): URI? {
        return try {
            // Should follow RFC-8089
            if (file.isInLocalFileSystem) {
                val path = if (file.path.startsWith("/")) "//${file.path}" else "///${file.path}"
                URI(file.fileSystem.protocol, null, path, null)
            } else {
                null
            }
        } catch (e: URISyntaxException) {
            getService(GlobalLogOutput::class.java).log("Could not transform ${file.url} to URI", ClientLogOutput.Level.DEBUG)
            null
        }
    }

    fun uriToVirtualFile(fileUri: URI): VirtualFile? {
        return try {
            VirtualFileManager.getInstance().findFileByUrl(URLDecoder.decode(fileUri.toString(), StandardCharsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            getService(GlobalLogOutput::class.java).log("Could not find file for URI $fileUri", ClientLogOutput.Level.DEBUG)
            null
        }
    }

    /** Checks a virtual file to be an actual file (not a directory) and contain non-binary information (text) */
    fun isNonBinaryFile(fileOrDir: VirtualFile): Boolean = when {
        fileOrDir.isDirectory
            || ProjectCoreUtil.isProjectOrWorkspaceFile(fileOrDir, fileOrDir.fileType)
            || fileOrDir.fileType.isBinary -> false

        else -> true
    }

    fun getFileContent(virtualFile: VirtualFile): String {
        val fileDocumentManager = FileDocumentManager.getInstance()
        if (fileDocumentManager.isFileModified(virtualFile)) {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                return document.text
            }
        }
        return virtualFile.contentsToByteArray().toString(virtualFile.charset)
    }
}
