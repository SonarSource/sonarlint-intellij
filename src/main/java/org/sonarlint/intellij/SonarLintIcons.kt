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
package org.sonarlint.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import java.awt.Color
import javax.swing.Icon
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType

object SonarLintIcons {

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
    val SONARLINT_ACTION_12PX = getIcon("/images/sonarlintAction_12px.svg")
    @JvmField
    val SONARLINT_ACTION_GREEN_12PX = getIcon("/images/sonarlintAction_green_12px.svg")

    @JvmField
    val SONARLINT_ACTION_ORANGE_12PX = getIcon("/images/sonarlintAction_orange_12px.svg")
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
    val RESOLVED = getIcon("/images/resolved.svg")
    @JvmField
    val FOCUS = getIcon("/images/focus.svg")

    private val SEVERITY_ICONS = mapOf(
        IssueSeverity.BLOCKER to getIcon("/images/severity/blocker.svg"),
        IssueSeverity.CRITICAL to getIcon("/images/severity/critical.svg"),
        IssueSeverity.INFO to getIcon("/images/severity/info.svg"),
        IssueSeverity.MAJOR to getIcon("/images/severity/major.svg"),
        IssueSeverity.MINOR to getIcon("/images/severity/minor.svg")
    )

    private val IMPACT_ICONS = mapOf(
        ImpactSeverity.BLOCKER to getIcon("/images/impact/blocker.svg"),
        ImpactSeverity.HIGH to getIcon("/images/impact/high.svg"),
        ImpactSeverity.MEDIUM to getIcon("/images/impact/medium.svg"),
        ImpactSeverity.LOW to getIcon("/images/impact/low.svg"),
        ImpactSeverity.INFO to getIcon("/images/impact/info.svg")
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

    val backgroundColorsByImpact = mapOf(
        ImpactSeverity.BLOCKER to JBColor(Color(254, 228, 226), Color(128, 27, 20, 20)),
        ImpactSeverity.HIGH to JBColor(Color(254, 243, 242), Color(253, 162, 155, 20)),
        ImpactSeverity.MEDIUM to JBColor(Color(255, 240, 235), Color(254, 150, 75, 20)),
        ImpactSeverity.LOW to JBColor(Color(252, 245, 228), Color(250, 220, 121, 20)),
        ImpactSeverity.INFO to JBColor(Color(245, 251, 255), Color(143, 202, 234, 20))
    )

    val fontColorsByImpact = mapOf(
        ImpactSeverity.BLOCKER to JBColor(Color(128, 27, 20), Color(249, 112, 102)),
        ImpactSeverity.HIGH to JBColor(Color(180, 35, 24), Color(253, 162, 155)),
        ImpactSeverity.MEDIUM to JBColor(Color(147, 55, 13), Color(254, 150, 75)),
        ImpactSeverity.LOW to JBColor(Color(140, 94, 30), Color(250, 220, 121)),
        ImpactSeverity.INFO to JBColor(Color(49, 107, 146), Color(143, 202, 234))
    )

    val borderColorsByImpact = mapOf(
        ImpactSeverity.BLOCKER to JBColor(Color(128, 27, 20), Color(249, 112, 102)),
        ImpactSeverity.HIGH to JBColor(Color(217, 44, 32), Color(253, 162, 155)),
        ImpactSeverity.MEDIUM to JBColor(Color(254, 150, 75), Color(254, 150, 75)),
        ImpactSeverity.LOW to JBColor(Color(250, 220, 121), Color(250, 220, 121)),
        ImpactSeverity.INFO to JBColor(Color(143, 202, 234), Color(143, 202, 234))
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
    fun impact(impact: ImpactSeverity): Icon {
        return IMPACT_ICONS[impact]!!
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
