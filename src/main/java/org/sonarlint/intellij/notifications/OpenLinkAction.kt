package org.sonarlint.intellij.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent

class OpenLinkAction(private val link: String, private val text: String) : NotificationAction(text) {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        BrowserUtil.browse(link)
    }

}
