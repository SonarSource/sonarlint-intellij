/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.editor

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.issue.QuickFix
import org.sonarlint.intellij.telemetry.SonarLintTelemetry

class ApplyQuickFixIntentionAction(private val fix: QuickFix, private val ruleKey: String) : IntentionAction, PriorityAction, Iconable {
    override fun getText() = "SonarLint: " + fix.message
    override fun getFamilyName() = "SonarLint quick fix"
    override fun startInWriteAction() = true
    override fun getIcon(flags: Int) = AllIcons.Actions.IntentionBulb
    override fun getPriority() = PriorityAction.Priority.TOP
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = fix.isApplicable()

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        fix.applied = true
        SonarLintUtils.getService(SonarLintTelemetry::class.java).addQuickFixAppliedForRule(ruleKey)
        // TODO Handle edits in other files!
        val currentFileEdits = fix.virtualFileEdits.filter { it.target == file.virtualFile }.flatMap { it.edits }
        currentFileEdits.forEach { (rangeMarker, newText) ->
            editor.document.replaceString(rangeMarker.startOffset, rangeMarker.endOffset, normalizeLineEndingsToLineFeeds(newText))
        }
        // formatting might be useful for multi-line edits
        CodeStyleManager.getInstance(project).reformatText(file, currentFileEdits.map { TextRange.create(it.rangeMarker) })
    }

    private fun normalizeLineEndingsToLineFeeds(text: String) = StringUtil.convertLineSeparators(text)
}
