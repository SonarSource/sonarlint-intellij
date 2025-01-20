/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.cayc

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.runOnPooledThread

@Service(Service.Level.APP)
class CleanAsYouCodeService {
    fun shouldFocusOnNewCode(project: Project): Boolean {
        return getGlobalSettings().isFocusOnNewCode
    }

    fun setFocusOnNewCode(isFocusOnNewCode: Boolean) {
        refresh(getGlobalSettings(), isFocusOnNewCode)
    }

    fun setFocusOnNewCode(isFocusOnNewCode: Boolean, settings: SonarLintGlobalSettings) {
        refresh(settings, isFocusOnNewCode)
    }

    private fun refresh(settings: SonarLintGlobalSettings, isFocusOnNewCode: Boolean) {
        if (settings.isFocusOnNewCode != isFocusOnNewCode) {
            settings.isFocusOnNewCode = isFocusOnNewCode
            runOnPooledThread {
                getService(BackendService::class.java).triggerTelemetryForFocusOnNewCode()
                ProjectManager.getInstance().openProjects.forEach { project ->
                    if (!project.isDisposed) {
                        getService(project, SonarLintToolWindow::class.java).refreshViews()
                        DaemonCodeAnalyzer.getInstance(project).restart()
                    }
                }
            }
        }
    }
}
