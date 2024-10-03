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

import com.intellij.openapi.application.Application
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import java.util.concurrent.atomic.AtomicReference
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.runReadActionSafely
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.SonarLintAppUtils.getRelativePathForAnalysis

fun Project.getOpenFiles() = FileEditorManager.getInstance(this).openFiles.toList()

fun Project.getRelativePathOf(file: VirtualFile) = getRelativePathForAnalysis(this, file)

fun Project.findModuleOf(file: VirtualFile): Module? {
    return findModuleForFile(file, this)
}

fun Project.openFileFrom(rangeMarker: RangeMarker?) {
    if (rangeMarker == null || !rangeMarker.isValid) {
        return
    }

    runReadActionSafely(this) {
        val psiFile = PsiDocumentManager.getInstance(this).getPsiFile(rangeMarker.document)
        if (psiFile != null && psiFile.isValid) {
            OpenFileDescriptor(this, psiFile.virtualFile, rangeMarker.startOffset).navigate(false)
        }
    }
}

fun VirtualFile.getDocument() = FileDocumentManager.getInstance().getDocument(this)

/**
 * Like Application.invokeAndWait, but handle the result
 */
fun <T> Application.computeInEDT(action: () -> T): T {
    val result = AtomicReference<T>()
    invokeAndWait {
        result.set(action.invoke())
    }
    return result.get()
}
