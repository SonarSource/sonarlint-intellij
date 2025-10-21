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
package org.sonarlint.intellij.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.DataKeys.Companion.ISSUE_DATA_KEY
import org.sonarlint.intellij.util.DataKeys.Companion.TAINT_VULNERABILITY_DATA_KEY

private const val ISSUE_TEXT_TITLE = "SonarQube: Fix with AI CodeFix"

class SuggestCodeFixIntentionAction(private val finding: Issue?) : AbstractSonarAction(
    "Fix with AI CodeFix", "Generate AI fix suggestion", SonarLintIcons.SPARKLE_GUTTER_ICON
), IntentionAction, PriorityAction, Iconable {

    override fun startInWriteAction() = false
    override fun getText(): String {
        return finding?.let {
            when (it) {
                is LiveIssue -> "$ISSUE_TEXT_TITLE '${it.message}'"
                is LocalTaintVulnerability -> "$ISSUE_TEXT_TITLE '${it.message()}'"
                else -> "$ISSUE_TEXT_TITLE '${it.getRuleKey()}'"
            }
        } ?: ISSUE_TEXT_TITLE
    }
    override fun getFamilyName() = "SonarQube AI codefix suggestion"
    override fun getPriority() = PriorityAction.Priority.HIGH
    override fun getIcon(flags: Int) = SonarLintIcons.SPARKLE_GUTTER_ICON

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = finding?.isAiCodeFixable() ?: false

    override fun isVisible(e: AnActionEvent): Boolean {
        var issue: Issue? = e.getData(ISSUE_DATA_KEY)
        if (issue == null) {
            issue = e.getData(TAINT_VULNERABILITY_DATA_KEY) ?: return false
        }
        return issue.isAiCodeFixable()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (finding == null) {
            return
        }
        startSuggestingCodeFix(project, finding)
    }

    companion object {
        fun startSuggestingCodeFix(project: Project, issue: Issue) {
            when (issue) {
                is LiveIssue -> startSuggestingCodeFixForIssue(project, issue)
                is LocalTaintVulnerability -> startSuggestingCodeFixForTaint(project, issue)
            }
        }

        private fun startSuggestingCodeFixForIssue(project: Project, issue: Issue) {
            runOnUiThread(project) {
                val toolWindow = getService(project, SonarLintToolWindow::class.java)
                toolWindow.openCurrentFileTab()
                toolWindow.bringToFront()

                getService(project, SonarLintToolWindow::class.java).trySelectIssueForCodeFix(issue.getId().toString())
            }
        }

        private fun startSuggestingCodeFixForTaint(project: Project, issue: Issue) {
            val serverKey = issue.getServerKey() ?: return
            runOnUiThread(project) {
                val toolWindow = getService(project, SonarLintToolWindow::class.java)
                toolWindow.openCurrentFileTab()
                toolWindow.bringToFront()

                getService(project, SonarLintToolWindow::class.java).trySelectTaintForCodeFix(serverKey)
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        var issue: Issue? = e.getData(ISSUE_DATA_KEY)
        if (issue == null) {
            issue = e.getData(TAINT_VULNERABILITY_DATA_KEY) ?: return
        }
        startSuggestingCodeFix(project, issue)
    }

}
