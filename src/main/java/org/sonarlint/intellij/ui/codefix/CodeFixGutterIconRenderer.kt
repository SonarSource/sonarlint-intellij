package org.sonarlint.intellij.ui.codefix

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import java.awt.event.MouseEvent
import javax.swing.Icon
import org.sonarlint.intellij.actions.SuggestCodeFixIntentionAction
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability

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

    override fun getIcon(): Icon {
        return AllIcons.Actions.Lightning
    }

    private class IssuePopupStep(private val project: Project, issues: List<Issue>) : BaseListPopupStep<Issue>(null, issues) {

        override fun getTextFor(issue: Issue): String {
            return when (issue) {
                is LiveIssue -> "SonarQube: Generate AI CodeFix for '${issue.message}'"
                is LocalTaintVulnerability -> "SonarQube: Generate AI CodeFix for '${issue.message()}'"
                else -> "SonarQube: Generate AI CodeFix for '${issue.getRuleKey()}'"
            }
        }

        override fun onChosen(selectedValue: Issue, finalChoice: Boolean): PopupStep<*>? {
            return doFinalStep {
                project.let { SuggestCodeFixIntentionAction.startSuggestingCodeFix(it, selectedValue) }
            }
        }
    }
}
