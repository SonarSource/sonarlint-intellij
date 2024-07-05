package org.sonarlint.intellij.ui.grip

import com.intellij.openapi.actionSystem.AnActionEvent
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService

class ReopenInlaySnippetAction() : AbstractSonarAction("Reopen Snippet") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        getService(project, SonarLintToolWindow::class.java).regenerateSnippet()
    }

}
