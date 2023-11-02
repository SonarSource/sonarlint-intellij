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
package org.sonarlint.intellij.cayc

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.ProjectManager
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.trigger.TriggerType

@Service(Service.Level.APP)
class CleanAsYouCodeService {
    fun shouldFocusOnNewCode() = getGlobalSettings().isFocusOnNewCode

    fun getNewCodeDefinitionDays() = getGlobalSettings().newCodeDefinitionDays

    fun setFocusOnNewCode(isFocusOnNewCode: Boolean) {
        refresh(getGlobalSettings(), isFocusOnNewCode)
    }

    fun setFocusOnNewCode(isFocusOnNewCode: Boolean, settings: SonarLintGlobalSettings) {
        refresh(settings, isFocusOnNewCode)
    }

    fun setNewCodeDefinition(newCodeDefinitionDays: Int, settings: SonarLintGlobalSettings) {
        refresh(settings, newCodeDefinitionDays)
    }

    private fun refresh(settings: SonarLintGlobalSettings, newCodeDefinitionDays: Int) {
        settings.newCodeDefinitionDays = newCodeDefinitionDays
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                SonarLintUtils.getService(project, AnalysisSubmitter::class.java).autoAnalyzeOpenFiles(TriggerType.CONFIG_CHANGE)
                SonarLintUtils.getService(project, SonarLintToolWindow::class.java).refreshViews()
            }
        }
    }

    private fun refresh(settings: SonarLintGlobalSettings, isFocusOnNewCode: Boolean) {
        settings.isFocusOnNewCode = isFocusOnNewCode
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                SonarLintUtils.getService(project, SonarLintToolWindow::class.java).refreshViews()
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }
}
