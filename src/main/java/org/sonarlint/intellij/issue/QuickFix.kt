/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.issue

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit
import org.sonarsource.sonarlint.core.analysis.api.TextEdit
import org.sonarsource.sonarlint.core.analysis.api.QuickFix as CoreQuickFix

fun convert(project: Project, coreQuickFix: CoreQuickFix): QuickFix? {
    val virtualFileEdits = coreQuickFix.inputFileEdits().map { convert(it) }
    if (virtualFileEdits.contains(null)) {
        log(project, "Quick fix won't be proposed as it is invalid")
        return null
    }
    if (virtualFileEdits.distinctBy { it!!.target }.size > 1) {
        log(project, "Quick fix won't be proposed because multi-files edits are not supported")
        return null
    }
    return QuickFix(coreQuickFix.message(), virtualFileEdits.mapNotNull { it })
}

private fun log(project: Project, message: String) {
    SonarLintConsole.get(project).debug(message)
}

private fun convert(fileEdit: ClientInputFileEdit): VirtualFileEdit? {
    val clientInputFile = fileEdit.target() as DefaultClientInputFile
    val targetVirtualFile = clientInputFile.clientObject
    val document = targetVirtualFile.getDocument() ?: return null
    if (clientInputFile.isOlderThan(document)) {
        // we don't want to show potentially outdated fixes
        // next analysis will bring more up-to-date quick fixes
        return null
    }
    val virtualFileEdits = fileEdit.textEdits().map { convert(document, it) }
    if (virtualFileEdits.contains(null)) {
        return null
    }
    return VirtualFileEdit(targetVirtualFile, virtualFileEdits.mapNotNull { it })
}

private fun convert(document: Document, textEdit: TextEdit): RangeMarkerEdit? {
    val range = textEdit.range()
    val lineCount = document.lineCount
    val beginLine = range.startLine?.minus(1) ?: -1
    val endLine = range.endLine?.minus(1) ?: -1
    if (beginLine < 0 || beginLine >= lineCount || endLine < 0 || endLine >= lineCount) {
        // range lines don't exist
        return null
    }
    val startOffset = document.getLineStartOffset(beginLine) + (range.startLineOffset ?:0)
    val endOffset = range.endLineOffset?.plus(document.getLineStartOffset(endLine)) ?: document.getLineEndOffset(endLine)
    if (startOffset > document.getLineEndOffset(beginLine) || endOffset > document.getLineEndOffset(endLine)) {
        // offset is greater than line length
        return null
    }
    // XXX should we dispose them at some point ?
    val rangeMarker = document.createRangeMarker(startOffset, endOffset)
    return RangeMarkerEdit(rangeMarker, textEdit.newText())
}

data class QuickFix(val message: String, val virtualFileEdits: List<VirtualFileEdit>) {
    var applied = false
    fun isApplicable() =
        !applied && virtualFileEdits.all { it.target.isValid && it.edits.all { e -> e.rangeMarker.isValid } }
}

data class VirtualFileEdit(val target: VirtualFile, val edits: List<RangeMarkerEdit>)

data class RangeMarkerEdit(val rangeMarker: RangeMarker, val newText: String)
