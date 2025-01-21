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
package org.sonarlint.intellij.editor

import com.intellij.codeInsight.intention.FileModifier
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
import org.sonarlint.intellij.finding.QuickFix
import org.sonarlint.intellij.finding.RangeMarkerEdit
import org.sonarlint.intellij.telemetry.SonarLintTelemetry

class ApplyQuickFixIntentionAction(private val fix: QuickFix, private val ruleKey: String, private val invokedInPreview: Boolean) : IntentionAction, PriorityAction, Iconable {
    override fun getText() = "SonarQube: " + fix.message
    override fun getFamilyName() = "SonarQube quick fix"
    override fun startInWriteAction() = true
    override fun getIcon(flags: Int) = AllIcons.Actions.IntentionBulb
    override fun getPriority() = PriorityAction.Priority.TOP
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = fix.isApplicable(editor.document)

    /** To differentiate if the quick fix was actually applied or only virtually for the preview */
    private fun invokedInPreview() = invokedInPreview

    /**
     *  [IntentionAction.generatePreview] expects a copy of the action on the virtual (temporary) file where the quick
     *  fix is applied for previewing. Therefore, the [ApplyQuickFixIntentionAction.invokedInPreview] has to be set to
     *  true for our implementation to not invoke telemetry and keeping the quick fix for the actual invocation. Also,
     *  we have to link the Psi of the actual file to the virtual one.
     */
    override fun getFileModifierForPreview(target: PsiFile): FileModifier {
        return ApplyQuickFixIntentionAction(fix, ruleKey, true)
    }

    /**
     *  This gets fired when we actually apply the quick fix and when the preview is generated in
     *  [IntentionAction.generatePreview]. Therefore, parts of the quick fix are not done when used in the preview like
     *  interacting with the telemetry.
     */
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!fix.isApplicable(editor.document)) {
            // the editor could have changed between the isAvailable and invoke calls
            return
        }

        var currentFileEdits = fix.virtualFileEdits.flatMap { it.edits }
        if (invokedInPreview()) {
            // edit range markers are tracking the real document, we need to convert them to track the preview document so that consecutive edits are correctly applied
            currentFileEdits = currentFileEdits.map { RangeMarkerEdit(editor.document.createRangeMarker(it.rangeMarker.startOffset, it.rangeMarker.endOffset), it.newText) }
        }
        currentFileEdits.forEach { (rangeMarker, newText) ->
            editor.document.replaceString(rangeMarker.startOffset, rangeMarker.endOffset, normalizeLineEndingsToLineFeeds(newText))
        }

        if (!invokedInPreview()) {
            // only when the quick fix was actually applied we want to remove it and interact with telemetry
            fix.applied = true
            SonarLintUtils.getService(SonarLintTelemetry::class.java).addQuickFixAppliedForRule(ruleKey)

            // formatting might be useful for multi-line edits
            CodeStyleManager.getInstance(project).reformatText(file, currentFileEdits.map { TextRange.create(it.rangeMarker) })
        }
    }

    private fun normalizeLineEndingsToLineFeeds(text: String) = StringUtil.convertLineSeparators(text)
}
