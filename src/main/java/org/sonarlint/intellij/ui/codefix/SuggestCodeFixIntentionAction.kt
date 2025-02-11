package org.sonarlint.intellij.ui.codefix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.DataKeys.Companion.ISSUE_DATA_KEY

class SuggestCodeFixIntentionAction(private val finding: LiveIssue?) : AbstractSonarAction(
    "Generate AI CodeFix", "Generate AI fix suggestion", null
), IntentionAction, PriorityAction, Iconable {

    override fun startInWriteAction() = false
    override fun getText() = "SonarQube: Generate AI codefix"
    override fun getFamilyName() = "SonarQube AI codefix suggestion"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true
    override fun isVisible(e: AnActionEvent) = true
    override fun getPriority() = PriorityAction.Priority.HIGH
    override fun getIcon(flags: Int) = AllIcons.Actions.IntentionBulb

    // TODO: Manage taint
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        showIssue(project)
    }

    private fun showIssue(project: Project, issue: Issue) {
        runOnUiThread(project) {
            val toolWindow = getService(project, SonarLintToolWindow::class.java)
            toolWindow.openCurrentFileTab()
            toolWindow.bringToFront()

            getService(project, SonarLintToolWindow::class.java).trySelectIssueForCodeFix(issue.getId().toString())
        }
    }

    private fun showIssue(project: Project) {
        if (finding == null) {
            return
        }
        showIssue(project, finding)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val issue: Issue = e.getData(ISSUE_DATA_KEY) ?: return
        showIssue(project, issue)
    }

}
