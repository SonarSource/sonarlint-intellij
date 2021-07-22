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

import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.client.api.common.QuickFix
import org.sonarsource.sonarlint.core.client.api.common.TextRange
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
    override fun getInputFile() =
        DefaultClientInputFile(file.virtualFile, file.name, false, file.virtualFile.charset)

    override fun getSeverity() = "MAJOR"
    override fun getType() = "BUG"
    override fun getRuleKey() = "ruleKey"
    override fun getRuleName() = "ruleName"
    override fun flows() = mutableListOf<Issue.Flow>()
    override fun quickFixes() = mutableListOf<QuickFix>()
}

private fun toTextRange(rangeMarker: RangeMarker?): TextRange? {
    return rangeMarker?.let {
        TextRange(1, 2, 3, 4)
    }
}
