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
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.filter.FiltersPanel
import org.sonarlint.intellij.ui.report.ReportPanel

/**
 * Page object for an open **Report** tool-window tab ([ReportPanel]).
 * Mirrors UI IT [org.sonarlint.intellij.its.tests.domain.ReportTabTests].
 */
class ReportTabPage internal constructor(
    private val project: Project,
    private val panel: ReportPanel,
) {

    val filters: ReportTabFiltersPanelPage = ReportTabFiltersPanelPage(project, panel)

    val visibleTexts: List<String>
        get() = readOnEdt {
            repeat(30) { pumpEdt() }
            collectPanelDisplayTexts(panel)
        }

    fun containsAllTexts(vararg expectedFragments: String): Boolean {
        val texts = visibleTexts
        return expectedFragments.all { fragment -> texts.any { it.contains(fragment) } }
    }
}

/** Filters on the Report tab (toolbar toggle + embedded [FiltersPanel]). */
class ReportTabFiltersPanelPage internal constructor(
    project: Project,
    private val reportPanel: ReportPanel,
) {

    private val filters = FiltersPanelPage(project, reportPanel.requireFiltersPanel())

    fun open() {
        readOnEdt {
            if (!reportPanel.isFiltersPanelVisible()) {
                reportPanel.showFiltersPanel(true)
            }
        }
    }

    fun close() {
        readOnEdt {
            if (reportPanel.isFiltersPanelVisible()) {
                reportPanel.showFiltersPanel(false)
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

internal fun ReportPanel.requireFiltersPanel(): FiltersPanel =
    walkComponents().filterIsInstance<FiltersPanel>().single()
