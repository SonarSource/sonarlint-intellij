package org.sonarlint.intellij.binding

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.sonarlint.intellij.notifications.AutoBindNotifications

class AutoBinding {

}

class AutoBindOnProjectOpen : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        AutoBindNotifications.sendNotification(project)
    }
}