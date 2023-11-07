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
package org.sonarlint.intellij.finding.issue

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import java.util.Optional
import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit
import org.sonarsource.sonarlint.core.analysis.api.Flow
import org.sonarsource.sonarlint.core.analysis.api.Issue
import org.sonarsource.sonarlint.core.analysis.api.QuickFix
import org.sonarsource.sonarlint.core.analysis.api.TextEdit
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextPointer
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextRange
import org.sonarsource.sonarlint.core.analysis.sonarapi.ActiveRuleAdapter
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue
import org.sonarsource.sonarlint.core.commons.ImpactSeverity
import org.sonarsource.sonarlint.core.commons.SoftwareQuality
import org.sonarsource.sonarlint.core.commons.api.TextRange
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetRuleDetailsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute

fun aLiveIssue(
    module: Module,
    file: PsiFile,
    rangeMarker: RangeMarker? = file.virtualFile.getDocument()!!.createRangeMarker(0, 1),
    coreIssue: RawIssue = aRawIssue(file, toTextRange(rangeMarker)),
): LiveIssue {
    val liveIssue = LiveIssue(module, coreIssue, file.virtualFile, rangeMarker, null, emptyList())
    liveIssue.serverFindingKey = "serverIssueKey"
    return liveIssue
}

fun aRawIssue(file: PsiFile, textRange: TextRange?) =
    RawIssue(aCoreIssue(file, textRange),
        GetRuleDetailsResponse(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.INFO, org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG, CleanCodeAttribute.COMPLETE, emptyList(), null))

fun aCoreIssue(file: PsiFile, textRange: TextRange? = TextRange(0, 0, 0, 1)) = Issue(
    ActiveRuleAdapter(ActiveRule("rule:key", "java")),
    "message",
    mutableMapOf(
        SoftwareQuality.MAINTAINABILITY to ImpactSeverity.HIGH,
        SoftwareQuality.RELIABILITY to ImpactSeverity.MEDIUM,
        SoftwareQuality.SECURITY to ImpactSeverity.LOW
    ),
    textRange?.let { aPluginApiTextRange(it) },
    aClientInputFile(file),
    mutableListOf<Flow>(),
    mutableListOf<QuickFix>(),
    Optional.empty()
)

private fun toTextRange(rangeMarker: RangeMarker?): TextRange? {
    return rangeMarker?.let {
        TextRange(1, 2, 3, 4)
    }
}

fun aClientInputFile(file: PsiFile) =
    DefaultClientInputFile(file.virtualFile, file.name, false, file.virtualFile.charset)

fun aClientInputFile(file: PsiFile, document: Document) =
    DefaultClientInputFile(file.virtualFile, file.name, false, file.virtualFile.charset, document.text, document.modificationStamp, null)

fun aQuickFix(message: String, fileEdits: List<ClientInputFileEdit>) = QuickFix(fileEdits, message)

fun aFileEdit(file: ClientInputFile, textEdits: List<TextEdit>) = ClientInputFileEdit(file, textEdits)

fun aTextEdit(range: TextRange, newText: String) = TextEdit(range, newText)

fun aTextRange(
    startLine: Int,
    startLineOffset: Int,
    endLine: Int,
    endLineOffset: Int,
) = TextRange(startLine, startLineOffset, endLine, endLineOffset)

fun aPluginApiTextRange(
    textRange: TextRange,
) = DefaultTextRange(DefaultTextPointer(textRange.startLine, textRange.startLineOffset), DefaultTextPointer(textRange.endLine, textRange.endLineOffset))

