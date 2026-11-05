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

import com.intellij.openapi.project.Project
import javax.swing.JToggleButton
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.currentfile.FindingsPanel
import org.sonarlint.intellij.ui.filter.FiltersPanel

/**
 * Filter bar on the Findings tab ([CurrentFileSummaryPanel] toggle + [FiltersPanel]).
 * Mirrors UI IT [org.sonarlint.intellij.its.utils.FiltersUtils.setFocusOnNewCode].
 */
class FindingsTabFiltersPanelPage internal constructor(
    project: Project,
    private val findingsPanel: FindingsPanel,
) {

    private val filters = FiltersPanelPage(project, findingsPanel.requireFiltersPanel())
    private val filterToggle = findingsPanel.requireFilterToggleButton()

    fun open() {
        readOnEdt {
            if (!filterToggle.isSelected) {
                filterToggle.doClick()
            }
        }
    }

    fun close() {
        readOnEdt {
            if (filterToggle.isSelected) {
                filterToggle.doClick()
            }
        }
    }

    fun setFocusOnNewCode(enabled: Boolean) {
        open()
        filters.setFocusOnNewCode(enabled)
        close()
    }

    fun resetFocusOnNewCode() = setFocusOnNewCode(false)
}

internal fun FindingsPanel.requireFiltersPanel(): FiltersPanel =
    walkComponents().filterIsInstance<FiltersPanel>().single()

internal fun FindingsPanel.requireFilterToggleButton(): JToggleButton =
    walkComponents().filterIsInstance<JToggleButton>().first { toggle ->
        toggle.toolTipText?.contains("Filter", ignoreCase = true) == true
    }
