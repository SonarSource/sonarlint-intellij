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

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.integration.DevoxxGenieBridge
import javax.swing.Icon

class FixWithDevoxxGenieIntentionAction(private val issue: LiveIssue) : IntentionAction, PriorityAction, Iconable {

    override fun startInWriteAction() = false

    override fun getText() = "DevoxxGenie: Fix '${issue.message}'"

    override fun getFamilyName() = "DevoxxGenie fix suggestion"

    override fun getPriority() = PriorityAction.Priority.NORMAL

    override fun getIcon(flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return issue.isValid() && DevoxxGenieBridge.isAvailable()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        FixWithLlmAction.fixWithLlm(project, issue, issue.getRuleKey(), issue.getRuleKey())
    }
}
