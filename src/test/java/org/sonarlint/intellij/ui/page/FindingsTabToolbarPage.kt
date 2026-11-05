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

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.currentfile.CurrentFileFindingsPanel
import org.sonarlint.intellij.ui.currentfile.FindingsPanel
import org.sonarlint.intellij.util.SonarLintActions

class FindingsTabToolbarPage internal constructor(
    private val findingsTab: FindingsTabPage,
    private val project: Project,
    private val panel: FindingsPanel,
) {

    val analyzeCurrentFileButton: FindingsTabAnalyzeCurrentFileButtonPage
        get() = readOnEdt {
            requireVisible()
            FindingsTabAnalyzeCurrentFileButtonPage(
                findAnalyzeCurrentFileButton(),
                this,
                findingsTab,
                project,
            )
        }

    val isVisible: Boolean
        get() = readOnEdt { visibilityProblem() == null }

    /** Fails when the Findings main toolbar is missing or hidden. */
    fun requireVisible() {
        readOnEdt {
            visibilityProblem()?.let { error(it) }
            pumpEdt()
        }
    }

    internal fun refreshActions() {
        readOnEdt {
            panel.findingsMainToolbar()?.updateActionsImmediately()
        }
    }

    private fun findAnalyzeCurrentFileButton(): ActionButton? {
        val analyzeAction = SonarLintActions.getInstance().analyzeCurrentFileAction()
        val buttons = toolbarActionButtons()
        return buttons.firstOrNull { it.action === analyzeAction }
            ?: buttons.firstOrNull { it.action.templateText == analyzeAction.templateText }
    }

    private fun toolbarActionButtons(): List<ActionButton> {
        panel.findingsMainToolbar()?.updateActionsImmediately()
        val roots = buildList {
            panel.findingsMainToolbar()?.component?.let { add(it) }
            if (panel is SimpleToolWindowPanel) {
                panel.getToolbar()?.let { add(it) }
            }
            add(panel)
        }
        return roots.flatMap { root ->
            root.walkComponents()
                .filter { it.javaClass.simpleName.contains("ActionButton") }
                .mapNotNull { it as? ActionButton }
        }
    }

    private fun visibilityProblem(): String? {
        if (panel !is SimpleToolWindowPanel) {
            return "Findings panel is not a SimpleToolWindowPanel"
        }
        if (panel.getToolbar() == null) {
            return "Findings toolbar is missing"
        }
        val toolbarComponent = panel.findingsMainToolbar()?.component
            ?: return "Findings main toolbar is missing"
        if (!toolbarComponent.isVisible) {
            return "Findings main toolbar component must be visible (production sets visibility in setToolbarSections)"
        }
        return null
    }
}

/** Returns the Findings tab [ActionToolbar] that production exposes in [CurrentFileFindingsPanel.setToolbarSections]. */
internal fun FindingsPanel.findingsMainToolbar(): ActionToolbar? {
    if (this !is CurrentFileFindingsPanel) {
        return null
    }
    val field = CurrentFileFindingsPanel::class.java.getDeclaredField("mainToolbar")
    field.isAccessible = true
    return field.get(this) as? ActionToolbar
}
