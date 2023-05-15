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
package org.sonarlint.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import org.sonarsource.sonarlint.core.commons.IssueSeverity
import org.sonarsource.sonarlint.core.commons.RuleType
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability
import java.awt.Color
import javax.swing.Icon

object SonarLintIcons {

    private fun colorWithAlpha(rgb: Int, alphaPercent: Int): Color {
        val alpha = alphaPercent * 255 / 100
        val rgba = rgb and 0xffffff or (alpha shl 24)
        return Color(rgba, true)
    }

    // From IntelliJ Platform UI Guidelines
    private val red60Color = colorWithAlpha(0xe05555, 60)
    private val orange60Color = colorWithAlpha(0xf26522, 60)
    private val yellow60Color = colorWithAlpha(0xf4af3d, 60)

    @JvmField
    val ICON_SONARQUBE = getIcon("/images/SonarQube.png")
    @JvmField
    val ICON_SONARCLOUD = getIcon("/images/SonarCloud.png")
    @JvmField
    val ICON_SONARQUBE_16 = getIcon("/images/onde-sonar-16.png")
    @JvmField
    val ICON_SONARCLOUD_16 = getIcon("/images/sonarcloud-16.png")
    @JvmField
    val SONARLINT_TOOLWINDOW = getIcon("/images/sonarlintToolWindow.svg")
    @JvmField
    val SONARLINT_ACTION = getIcon("/images/sonarlintAction.svg")
    @JvmField
    val SONARLINT_TOOLWINDOW_EMPTY = getIcon("/images/sonarlintToolWindowEmpty.svg")
    @JvmField
    val SONARLINT = getIcon("/images/sonarlint.png")
    @JvmField
    val SONARLINT_32 = getIcon("/images/sonarlint@2x.png")
    @JvmField
    val PLAY = getIcon("/images/execute.png")
    @JvmField
    val CLEAN = getIcon("/images/clean.png")
    @JvmField
    val TOOLS = getIcon("/images/externalToolsSmall.png")
    @JvmField
    val SUSPEND = getIcon("/images/suspend.png")
    @JvmField
    val INFO = getIcon("/images/info.png")
    @JvmField
    val WARN = getIcon("/images/warn.png")
    @JvmField
    val SCM = getIcon("/images/toolWindowChanges.png")
    @JvmField
    val PROJECT = getIcon("/images/ideaProject.png")
    @JvmField
    val NOT_CONNECTED = getIcon("/images/not_connected.svg")
    @JvmField
    val CONNECTED = getIcon("/images/connected.svg")
    @JvmField
    val CONNECTION_ERROR = getIcon("/images/io_error.svg")
    @JvmField
    val HOTSPOT_CHECKED = getIcon("/images/hotspot_checked.svg")

    private val SEVERITY_ICONS = mapOf(
        IssueSeverity.BLOCKER to getIcon("/images/severity/blocker.svg"),
        IssueSeverity.CRITICAL to getIcon("/images/severity/critical.svg"),
        IssueSeverity.INFO to getIcon("/images/severity/info.svg"),
        IssueSeverity.MAJOR to getIcon("/images/severity/major.svg"),
        IssueSeverity.MINOR to getIcon("/images/severity/minor.svg")
    )
    private val TYPE_ICONS = mapOf(
        RuleType.BUG to getIcon("/images/type/bug.svg"),
        RuleType.CODE_SMELL to getIcon("/images/type/codeSmell.svg"),
        RuleType.VULNERABILITY to getIcon("/images/type/vulnerability.svg"),
        RuleType.SECURITY_HOTSPOT to getIcon("/images/type/hotspot.svg")
    )
    private val PROBABILITY_ICONS = mapOf(
        VulnerabilityProbability.HIGH to getIcon("/images/type/hotspotHigh.svg"),
        VulnerabilityProbability.MEDIUM to getIcon("/images/type/hotspotMedium.svg"),
        VulnerabilityProbability.LOW to getIcon("/images/type/hotspotLow.svg")
    )

    val colorsByProbability = mapOf(
        VulnerabilityProbability.HIGH to JBColor(red60Color, red60Color),
        VulnerabilityProbability.MEDIUM to JBColor(orange60Color, orange60Color),
        VulnerabilityProbability.LOW to JBColor(yellow60Color, yellow60Color)
    )

    private fun getIcon(path: String): Icon {
        return IconLoader.getIcon(path, SonarLintIcons::class.java)
    }

    @JvmStatic
    fun severity(severity: IssueSeverity): Icon {
        return SEVERITY_ICONS[severity]!!
    }

    @JvmStatic
    fun type(type: RuleType): Icon {
        return TYPE_ICONS[type]!!
    }

    @JvmStatic
    fun toDisabled(icon: Icon): Icon {
        return IconLoader.getDisabledIcon(icon)
    }

    @JvmStatic
    fun hotspotTypeWithProbability(vulnerabilityProbability: VulnerabilityProbability): Icon {
        return PROBABILITY_ICONS[vulnerabilityProbability]!!
    }
}
