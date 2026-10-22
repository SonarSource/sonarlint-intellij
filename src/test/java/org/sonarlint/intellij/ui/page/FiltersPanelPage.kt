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
import java.util.concurrent.TimeUnit
import org.awaitility.Awaitility
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.filter.FiltersPanel

/** Page object for a production [FiltersPanel] (Findings or Report tab). */
class FiltersPanelPage internal constructor(
    private val project: Project,
    private val panel: FiltersPanel,
) {

    val isVisible: Boolean
        get() = readOnEdt { panel.isVisible }

    val focusOnNewCodeSelected: Boolean
        get() = readOnEdt { panel.focusOnNewCodeCheckBox.isSelected }

    fun setFocusOnNewCode(enabled: Boolean) {
        readOnEdt {
            val checkbox = panel.focusOnNewCodeCheckBox
            if (checkbox.isSelected != enabled) {
                checkbox.doClick()
            }
        }
        Awaitility.await()
            .pollInSameThread()
            .atMost(30, TimeUnit.SECONDS)
            .until {
                getService(project, CleanAsYouCodeService::class.java).shouldFocusOnNewCode() == enabled
            }
        repeat(30) { pumpEdt() }
    }

    fun resetFocusOnNewCode() = setFocusOnNewCode(false)
}
