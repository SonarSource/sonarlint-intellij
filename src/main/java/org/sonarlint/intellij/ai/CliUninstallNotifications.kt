/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.ai

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTextArea
import javax.swing.JComponent
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.projectLessNotification
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse

internal fun notifyCliUninstallResult(feedback: CliUninstallFeedback.Finished) {
    val type = when (feedback.status) {
        UninstallCliResponse.Status.UNINSTALLED -> if (feedback.tokenRevocationWarning == null) NotificationType.INFORMATION else NotificationType.WARNING
        UninstallCliResponse.Status.NOT_AVAILABLE -> NotificationType.WARNING
        UninstallCliResponse.Status.FAILED -> NotificationType.ERROR
    }
    val message = listOfNotNull(feedback.summary.substringBefore("\n\n"), feedback.tokenRevocationWarning).joinToString("\n\n")
    projectLessNotification(CLI_UNINSTALL_TITLE, message, type, NotificationAction.createSimple("Show details") {
        object : DialogWrapper(null, true) {
            init {
                title = CLI_UNINSTALL_TITLE
                init()
            }

            override fun createCenterPanel(): JComponent = ScrollPaneFactory.createScrollPane(JBTextArea(
                listOfNotNull(
                    feedback.summary,
                    feedback.tokenRevocationWarning,
                    feedback.backendMessage?.takeIf { it.isNotBlank() && it != feedback.summary },
                    "Reset output:\n${feedback.resetOutput}",
                    "Reset errors and warnings:\n${feedback.cleanupWarnings}"
                ).joinToString("\n\n"),
                18, 80
            ).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
            })
        }.show()
    })
}
