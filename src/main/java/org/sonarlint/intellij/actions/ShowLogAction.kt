package org.sonarlint.intellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

class ShowLogAction : AbstractSonarAction("Show Log") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runOnUiThread(project) {
            getService(project, SonarLintToolWindow::class.java).openLogTab()
        }
    }

}
