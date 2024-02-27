package org.sonarlint.intellij.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.runOnPooledThread

class RestartSloopAction : NotificationAction("Restart SonarLint") {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        runOnPooledThread {
            SonarLintUtils.getService(BackendService::class.java).restartTest()
        }
        notification.expire()
    }

}
