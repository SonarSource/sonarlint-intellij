/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.finding.hotspot

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter

sealed class SecurityHotspotsStatus

object NoBinding : SecurityHotspotsStatus()

object InvalidBinding : SecurityHotspotsStatus()
object ValidStatus: SecurityHotspotsStatus()

class SecurityHotspotsPresenter(private val project: Project) {

    fun presentSecurityHotspotsForOpenFiles() {
        // TODO update the logic similar to TaintVulnerabilities
        val status = if (!Settings.getSettingsFor(project).isBindingEnabled) NoBinding else {
            ValidStatus
        }
        ApplicationManager.getApplication().invokeLater({
            SonarLintUtils.getService(project, SonarLintToolWindow::class.java).populateSecurityHotspotsTab(status)
            if (status is ValidStatus) {
                SonarLintUtils.getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
            }
        }, ModalityState.defaultModalityState(), project.disposed)
    }

    fun refreshSecurityHotspotsForOpenFiles() {
        presentSecurityHotspotsForOpenFiles()
    }
}
