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
package org.sonarlint.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import java.awt.Point
import javax.swing.JComponent
import org.sonarlint.intellij.SonarLintIcons

object SonarGotItTooltipsUtils {

    private const val TRAFFIC_LIGHT_TOOLTIP_ID = "sonarlint.traffic.light.tooltip"
    private const val TRAFFIC_LIGHT_TOOLTIP_TEXT = """See how many issues need your attention in the current file. 
        Click the icon to show/hide the SonarLint tool window, and hover to view more actions."""

    fun showTrafficLightToolTip(component: JComponent, parent: Disposable) {
        // we pick a random project service that is disposable
        with(GotItTooltip(TRAFFIC_LIGHT_TOOLTIP_ID, TRAFFIC_LIGHT_TOOLTIP_TEXT, parent)) {
            withIcon(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
            withPosition(Balloon.Position.above)
            show(component) { it, _ -> Point(-5, it.height / 2) }
        }
    }

}
