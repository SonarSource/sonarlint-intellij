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
package org.sonarlint.intellij.ui.codefix

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.SuggestCodeFixIntentionAction
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import java.awt.event.MouseEvent

private const val ISSUE_TEXT_TITLE = "SonarQube: Fix with AI CodeFix"

class CodeFixGutterIconRenderer(val editor: Editor, val line: Int, val issues: List<Issue>) : GutterIconRenderer() {

    override fun getClickAction(): AnAction {
        return object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                val step = IssuePopupStep(project, issues)
                val mouseLocation = (e.inputEvent as MouseEvent).locationOnScreen
                JBPopupFactory.getInstance()
                    .createListPopup(step)
                    .apply {
                        showInScreenCoordinates(editor.component, mouseLocation)
                    }
            }
        }
    }
    override fun isNavigateAction() = true
    override fun equals(other: Any?) = icon == (other as? CodeFixGutterIconRenderer)?.icon
    override fun hashCode() = icon.hashCode()
    override fun getIcon() = SonarLintIcons.SPARKLE_GUTTER_ICON

    private class IssuePopupStep(private val project: Project, issues: List<Issue>) : BaseListPopupStep<Issue>(null, issues) {
        override fun getTextFor(issue: Issue): String {
            return when (issue) {
                is LiveIssue -> "$ISSUE_TEXT_TITLE '${issue.message}'"
                is LocalTaintVulnerability -> "$ISSUE_TEXT_TITLE '${issue.message()}'"
                else -> "$ISSUE_TEXT_TITLE '${issue.getRuleKey()}'"
            }
        }

        override fun onChosen(selectedValue: Issue, finalChoice: Boolean): PopupStep<*>? {
            return doFinalStep {
                project.let { SuggestCodeFixIntentionAction.startSuggestingCodeFix(it, selectedValue) }
            }
        }
    }

}
