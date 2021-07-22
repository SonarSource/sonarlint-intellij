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
package org.sonarlint.intellij.editor

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.issue.QuickFix
import java.util.function.Consumer

class ApplyQuickFixIntentionAction(private val fix: QuickFix) : IntentionAction, PriorityAction, Iconable {
    override fun getText() = "SonarLint: " + fix.message
    override fun getFamilyName() = "SonarLint quick fix"
    override fun startInWriteAction() = true
    override fun getIcon(flags: Int) = AllIcons.Actions.QuickfixBulb
    override fun getPriority() = PriorityAction.Priority.NORMAL
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = fix.isApplicable()

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        fix.applied = true
        fix.virtualFileEdits.forEach(Consumer { (target, edits) ->
            if (target == file.virtualFile) {
                edits.forEach(Consumer { (rangeMarker, newText) ->
                    editor.document.replaceString(rangeMarker.startOffset, rangeMarker.endOffset, newText)
                })
            } else {
                // TODO Handle edits in other files!
            }
        })
    }
}
