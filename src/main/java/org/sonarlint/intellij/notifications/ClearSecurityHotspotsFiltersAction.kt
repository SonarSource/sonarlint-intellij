/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.util.SonarLintActions

class ClearSecurityHotspotsFiltersAction(private val securityHotspotKey: String) : NotificationAction("Clear filters and retry") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        e.project?.let {
            SonarLintActions.getInstance().filterSecurityHotspots().resetToDefaultSettings(e)
            SonarLintActions.getInstance().includeResolvedHotspotAction().setSelected(e, true)
            if (!SonarLintUtils.getService(it, SonarLintToolWindow::class.java).trySelectSecurityHotspot(securityHotspotKey)) {
                SonarLintProjectNotifications.get(it)
                    .notifyUnableToOpenFinding("The Security Hotspot could not be opened by SonarQube for IntelliJ")
            }
        }
        notification.expire()
    }
}
