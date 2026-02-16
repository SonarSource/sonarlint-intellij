/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.actions

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.integration.DevoxxGenieBridge
import kotlin.math.max
import kotlin.math.min

object FixWithLlmAction {

    fun canFixWithLlm(): Boolean = DevoxxGenieBridge.isAvailable()

    fun fixWithLlm(project: Project, finding: LiveFinding, ruleName: String, ruleKey: String): Boolean {
        val prompt = buildPrompt(finding, ruleName, ruleKey) ?: return false
        return DevoxxGenieBridge.sendPrompt(project, prompt)
    }

    private fun buildPrompt(finding: LiveFinding, ruleName: String, ruleKey: String): String? {
        val file = finding.file() ?: return null
        val range = finding.range ?: return null

        val lineAndSnippet = computeReadActionSafely(file) {
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@computeReadActionSafely null
            val line = document.getLineNumber(range.startOffset) + 1
            val startLine = max(0, line - 1 - 10)
            val endLine = min(document.lineCount - 1, line - 1 + 10)
            val startOffset = document.getLineStartOffset(startLine)
            val endOffset = document.getLineEndOffset(endLine)
            val snippet = document.getText(TextRange(startOffset, endOffset))
            Pair(line, snippet)
        } ?: return null

        val (line, snippet) = lineAndSnippet

        return buildString {
            appendLine("Fix the following SonarQube issue in my code.")
            appendLine()
            appendLine("**Rule:** $ruleName (`$ruleKey`)")
            appendLine("**Issue:** ${finding.message}")
            appendLine("**File:** `${file.path}`")
            appendLine("**Line:** $line")
            appendLine()
            appendLine("**Code context:**")
            appendLine("```")
            appendLine(snippet)
            appendLine("```")
            appendLine()
            appendLine("Please suggest a fix for this issue. Explain what the problem is and provide the corrected code.")
        }
    }
}
