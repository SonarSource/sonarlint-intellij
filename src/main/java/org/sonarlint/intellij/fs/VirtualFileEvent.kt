package org.sonarlint.intellij.fs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import java.nio.charset.Charset
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

data class VirtualFileEvent(
    val type: ModuleFileEvent.Type,
    val virtualFile: VirtualFile,
) {

    fun getEncoding(project: Project): Charset {
        val encodingProjectManager = EncodingProjectManager.getInstance(project)
        val encoding = encodingProjectManager.getEncoding(virtualFile, true)
        return encoding ?: Charset.defaultCharset()
    }

}