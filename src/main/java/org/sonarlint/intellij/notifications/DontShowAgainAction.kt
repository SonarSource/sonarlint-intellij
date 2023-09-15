package org.sonarlint.intellij.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent

class DontShowAgainAction(private val id: String) : NotificationAction("Don't show again") {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        PropertiesComponent.getInstance().setValue(id, true)
        notification.expire()
    }

}
