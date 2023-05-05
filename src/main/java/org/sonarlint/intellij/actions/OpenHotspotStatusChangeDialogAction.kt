package org.sonarlint.intellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent

class OpenHotspotStatusChangeDialogAction : AbstractSonarAction(
    "Change Status",
    "Open hotspot status change in dialog",
    null
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        println("Open in Dialog")
    }
}