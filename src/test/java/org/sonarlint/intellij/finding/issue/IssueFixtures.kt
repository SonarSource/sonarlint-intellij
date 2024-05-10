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
import java.net.URI
import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.util.VirtualFileUtils
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.FileEditDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.QuickFixDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueFlowDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.TextEditDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto

fun aLiveIssue(
    module: Module,
    file: PsiFile,
    rangeMarker: RangeMarker? = file.virtualFile.getDocument()!!.createRangeMarker(0, 1),
    coreIssue: RawIssueDto = aRawIssue(file, toTextRange(rangeMarker)),
): LiveIssue {
    val liveIssue = LiveIssue(module, coreIssue, file.virtualFile, rangeMarker, null, emptyList())
    liveIssue.serverFindingKey = "serverIssueKey"
    return liveIssue
}

fun aRawIssue(file: PsiFile, textRange: TextRangeDto?) =
    RawIssueDto(
        IssueSeverity.INFO,
        RuleType.BUG,
        CleanCodeAttribute.COMPLETE,
        mutableMapOf(
            SoftwareQuality.MAINTAINABILITY to ImpactSeverity.HIGH,
            SoftwareQuality.RELIABILITY to ImpactSeverity.MEDIUM,
            SoftwareQuality.SECURITY to ImpactSeverity.LOW
        ),
        "rule:key",
        "message",
        VirtualFileUtils.toURI(file.virtualFile),
        mutableListOf<RawIssueFlowDto>(),
        mutableListOf<QuickFixDto>(),
        textRange,
        null, null
    )

private fun toTextRange(rangeMarker: RangeMarker?): TextRangeDto? {
    return rangeMarker?.let {
        TextRangeDto(1, 2, 3, 4)
    }
}

fun aClientInputFile(file: PsiFile, document: Document) =
    DefaultClientInputFile(file.virtualFile, file.name, false, file.virtualFile.charset, document.text, document.modificationStamp, null)

fun aQuickFix(message: String, fileEdits: List<FileEditDto>) = QuickFixDto(fileEdits, message)

fun aFileEdit(fileUri: URI, textEdits: List<TextEditDto>) = FileEditDto(fileUri, textEdits)

fun aTextEdit(range: TextRangeDto, newText: String) = TextEditDto(range, newText)

fun aTextRange(
    startLine: Int,
    startLineOffset: Int,
    endLine: Int,
    endLineOffset: Int,
) = TextRangeDto(startLine, startLineOffset, endLine, endLineOffset)
