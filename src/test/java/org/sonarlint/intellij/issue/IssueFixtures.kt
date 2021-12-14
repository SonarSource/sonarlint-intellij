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
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit
import org.sonarsource.sonarlint.core.analysis.api.Flow
import org.sonarsource.sonarlint.core.analysis.api.QuickFix
import org.sonarsource.sonarlint.core.analysis.api.TextEdit
import org.sonarsource.sonarlint.core.analysis.api.TextRange
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue

fun aLiveIssue(
    file: PsiFile,
    rangeMarker: RangeMarker? = file.virtualFile.getDocument()!!.createRangeMarker(0, 1),
    coreIssue: Issue = aCoreIssue(file, toTextRange(rangeMarker))
): LiveIssue {
    val liveIssue = LiveIssue(coreIssue, file, rangeMarker, null, emptyList())
    liveIssue.serverIssueKey = "serverIssueKey"
    return liveIssue
}

fun aCoreIssue(file: PsiFile, textRange: TextRange? = TextRange(0, 0, 0, 1)) = object : Issue {
    override fun getTextRange() = textRange
    override fun getMessage() = "message"
    override fun getInputFile() = aClientInputFile(file)

    override fun getSeverity() = "MAJOR"
    override fun getType() = "BUG"
    override fun getRuleKey() = "ruleKey"
    override fun flows() = mutableListOf<Flow>()
    override fun quickFixes() = mutableListOf<QuickFix>()
}

private fun toTextRange(rangeMarker: RangeMarker?): TextRange? {
    return rangeMarker?.let {
        TextRange(1, 2, 3, 4)
    }
}

fun aClientInputFile(file: PsiFile) =
    DefaultClientInputFile(file.virtualFile, file.name, false, file.virtualFile.charset)

fun aClientInputFile(file: PsiFile, document: Document) =
    DefaultClientInputFile(file.virtualFile, file.name, false, file.virtualFile.charset, document.text, document.modificationStamp, null)

fun aQuickFix(message: String, fileEdits: List<ClientInputFileEdit>) = object : QuickFix {
    override fun inputFileEdits() = fileEdits
    override fun message() = message
}

fun aFileEdit(file: ClientInputFile, textEdits: List<TextEdit>) = object : ClientInputFileEdit {
    override fun target() = file
    override fun textEdits() = textEdits
}

fun aTextEdit(range: TextRange, newText: String) = object : TextEdit {
    override fun range() = range
    override fun newText() = newText
}

fun aTextRange(
    startLine: Int,
    startLineOffset: Int,
    endLine: Int,
    endLineOffset: Int
) = TextRange(startLine, startLineOffset, endLine, endLineOffset)

