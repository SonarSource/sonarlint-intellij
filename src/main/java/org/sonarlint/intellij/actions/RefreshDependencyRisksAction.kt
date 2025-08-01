package org.sonarlint.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.runOnPooledThread

class RefreshDependencyRisksAction(text: String = "Refresh") : AbstractSonarAction(text, "Refresh Dependency Risks", AllIcons.Actions.Refresh) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        runOnPooledThread {
            getService(BackendService::class.java).refreshDependencyRisks(project)
        }
    }
}
