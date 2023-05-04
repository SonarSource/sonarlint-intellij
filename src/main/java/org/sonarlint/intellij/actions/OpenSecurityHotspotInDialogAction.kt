package org.sonarlint.intellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class OpenSecurityHotspotInDialogAction : AbstractSonarAction(
    "Open In Dialog",
    "Open security hotspot in dialog",
    null
) {
    override fun updatePresentation(e: AnActionEvent, project: Project) {
        e.presentation.text = "Open in Dialog"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        println("Open in Dialog")
    }
}