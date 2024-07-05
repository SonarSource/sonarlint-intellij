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
package org.sonarlint.intellij.ui.grip

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.finding.Finding

class SuggestAiFixIntentionAction(
    private val finding: Finding,
) :
    IntentionAction, PriorityAction, Iconable {
    override fun startInWriteAction() = true
    override fun getText() = "SonarLint: Suggest fix for ${finding.getRuleKey()}"
    override fun getFamilyName() = "SonarLint fix suggestion"
    override fun getPriority() = PriorityAction.Priority.HIGH
    override fun getIcon(flags: Int) = AllIcons.Actions.IntentionBulb
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        SuggestAiFixAction.suggestAiFix(project, finding)
    }

}
