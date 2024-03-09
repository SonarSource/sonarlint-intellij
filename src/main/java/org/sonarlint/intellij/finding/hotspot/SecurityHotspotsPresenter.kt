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
package org.sonarlint.intellij.finding.hotspot

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

sealed class SecurityHotspotsLocalDetectionSupport

data class NotSupported(val reason: String) : SecurityHotspotsLocalDetectionSupport()
object Supported : SecurityHotspotsLocalDetectionSupport()

@Service(Service.Level.PROJECT)
class SecurityHotspotsPresenter(private val project: Project) {
    fun presentSecurityHotspotsForOpenFiles() {
        getService(BackendService::class.java)
            .checkLocalSecurityHotspotDetectionSupported(project)
            .thenApplyAsync { response -> if (response.isSupported) Supported else NotSupported(response.reason!!) }
            .thenAcceptAsync { status ->
                runOnUiThread(project) {
                    getService(project, SonarLintToolWindow::class.java).populateSecurityHotspotsTab(status)
                    if (status is Supported) {
                        getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
                    }
                }
            }
    }
}
