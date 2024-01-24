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
package org.sonarlint.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import java.awt.Point
import java.net.URL
import javax.swing.JComponent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CLEAN_CODE_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.FOCUS_ON_NEW_CODE_LINK

object SonarGotItTooltipsUtils {

    private const val FOCUS_NEW_CODE_TOOLTIP_ID = "sonarlint.focus.new.code.tooltip"
    private const val FOCUS_NEW_CODE_TOOLTIP_TEXT = """Deliver clean code by focusing on code that was recently modified"""

    private const val CLEAN_CODE_TOOLTIP_ID = "sonarlint.clean.code.tooltip"
    private const val CLEAN_CODE_TOOLTIP_TEXT = """We have refined Sonar issues: Clean Code attributes spotlight what characteristic of Clean Code was violated. 
            Software qualities represent the impact of an issue on your application."""

    private const val TRAFFIC_LIGHT_TOOLTIP_ID = "sonarlint.traffic.light.tooltip"
    private const val TRAFFIC_LIGHT_TOOLTIP_TEXT = """See how many issues need your attention in the current file. 
        Click the icon to show/hide the SonarLint tool window, and hover to view more actions."""

    fun showFocusOnNewCodeToolTip(component: JComponent, parent: Disposable) {
        with(GotItTooltip(FOCUS_NEW_CODE_TOOLTIP_ID, FOCUS_NEW_CODE_TOOLTIP_TEXT, parent)) {
            withIcon(SonarLintIcons.SONARLINT)
            withPosition(Balloon.Position.atRight)
            withBrowserLink("Learn more about Clean as You Code", URL(FOCUS_ON_NEW_CODE_LINK))
            // Point at the first action level
            show(component) { _, _ -> Point(0, 15) }
        }
    }

    fun showCleanCodeToolTip(component: JComponent, parent: Disposable) {
        with(GotItTooltip(CLEAN_CODE_TOOLTIP_ID, CLEAN_CODE_TOOLTIP_TEXT, parent)) {
            withBrowserLink("Learn more about Clean Code", URL(CLEAN_CODE_LINK))
            withIcon(SonarLintIcons.SONARLINT)
            withPosition(Balloon.Position.atLeft)
            show(component, GotItTooltip.LEFT_MIDDLE)
        }
    }

    fun showTrafficLightToolTip(component: JComponent, parent: Disposable) {
        // we pick a random project service that is disposable
        with(GotItTooltip(TRAFFIC_LIGHT_TOOLTIP_ID, TRAFFIC_LIGHT_TOOLTIP_TEXT, parent)) {
            withIcon(SonarLintIcons.SONARLINT)
            withPosition(Balloon.Position.above)
            show(component) { it, _ -> Point(-5, it.height / 2) }
        }
    }

}
