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
package org.sonarlint.intellij.ui.page

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.currentfile.FindingsPanel

class FindingsTabToolbarPage internal constructor(
    private val project: Project,
    private val panel: FindingsPanel,
) {

    val isVisible: Boolean
        get() = panel.walkComponents()
            .any { it.javaClass.simpleName.contains("ActionToolbar", ignoreCase = true) }

    val isAnalyzeCurrentFileEnabled: Boolean
        get() {
            syncFindingsPanelWithSelectedEditor(project, panel)
            return analyzeCurrentFileButton()?.isEnabled == true
        }

    fun analyzeCurrentFile() {
        syncFindingsPanelWithSelectedEditor(project, panel)
        val button = analyzeCurrentFileButton()
            ?: error("Analyze Current File toolbar button not found on Findings tab")
        check(button.isEnabled) { "Analyze Current File action is not enabled" }
        button.click()
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun analyzeCurrentFileButton(): ActionButton? =
        panel.findComponents<ActionButton>()
            .firstOrNull { it.action.templatePresentation.text == ANALYZE_CURRENT_FILE_TEXT }

    private companion object {
        private const val ANALYZE_CURRENT_FILE_TEXT = "Analyze Current File"
    }
}
