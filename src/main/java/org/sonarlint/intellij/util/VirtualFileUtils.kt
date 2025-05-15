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

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import io.ktor.utils.io.charsets.name
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.Charset
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
        } catch (_: URISyntaxException) {
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

    fun getFileContent(virtualFile: VirtualFile): String {
        val fileDocumentManager = FileDocumentManager.getInstance()
        if (virtualFile.extension == "ipynb" || fileDocumentManager.isFileModified(virtualFile)) {
            val document = fileDocumentManager.getDocument(virtualFile)
            if (document != null) {
                return if (virtualFile.extension == "ipynb") {
                    removeMarkdownCells(document.text)
                } else {
                    document.text
                }
            }
        }
        return virtualFile.contentsToByteArray().toString(virtualFile.charset)
    }

    fun removeMarkdownCells(fileContent: String): String {
        var isMarkdown = false
        val result = StringBuilder()
        for (line in fileContent.lines()) {
            if (line.startsWith("#%% md") || line.startsWith("#%% raw")) {
                isMarkdown = true
            } else if (line.startsWith("#%%")) {
                isMarkdown = false
            }

            if (!isMarkdown) {
                result.appendLine(line)
            }
        }
        return result.toString().trimEnd()
    }

    fun getEncoding(virtualFile: VirtualFile, project: Project): String {
        if (virtualFile.isCharsetSet) {
            return virtualFile.charset.name
        }
        val encodingProjectManager = EncodingProjectManager.getInstance(project)
        val encoding = encodingProjectManager.getEncoding(virtualFile, true)
        return encoding?.name ?: Charset.defaultCharset().name
    }

}
