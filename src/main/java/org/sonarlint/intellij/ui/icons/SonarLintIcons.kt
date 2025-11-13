/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.ui.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import java.awt.Color
import javax.swing.Icon
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType

object SonarLintIcons {

    @JvmField
    val ICON_SONARQUBE_SERVER = getIcon("/images/sonarqube_server.png")
    @JvmField
    val ICON_SONARQUBE_CLOUD = getIcon("/images/sonarqube_cloud.png")
    @JvmField
    val ICON_SONARQUBE_SERVER_16 = getIcon("/images/sonarqube_server_16px.svg")
    @JvmField
    val ICON_SONARQUBE_CLOUD_16 = getIcon("/images/sonarqube_cloud_16px.svg")
    @JvmField
    val SONARQUBE_FOR_INTELLIJ_13PX = getIcon("/images/sonarqube_for_intellij_13px.svg")
    @JvmField
    val SONARQUBE_FOR_INTELLIJ_RED_13PX = getIcon("/images/sonarqube_for_intellij_red_13px.svg")
    @JvmField
    val SONARQUBE_FOR_INTELLIJ_GREEN_13PX = getIcon("/images/sonarqube_for_intellij_green_13px.svg")
    @JvmField
    val SONARQUBE_FOR_INTELLIJ_ORANGE_13PX = getIcon("/images/sonarqube_for_intellij_orange_13px.svg")

    @JvmField
    val SONARQUBE_FOR_INTELLIJ_TOOLWINDOW = getIcon("/images/sonarqube_for_intellij_toolwindow.svg")

    @JvmField
    val SONARQUBE_FOR_INTELLIJ_EMPTY_TOOLWINDOW = getIcon("/images/sonarqube_for_intellij_empty_toolwindow.svg")
    @JvmField
    val SONARQUBE_FOR_INTELLIJ = getIcon("/images/sonarqube_for_intellij.svg")
    @JvmField
    val SONARQUBE_FOR_INTELLIJ_32PX = getIcon("/images/sonarqube_for_intellij_32px.svg")
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
    val CODEFIX_PRESENTATION = getIcon("/images/codefix/presentation.svg")
    @JvmField
    val SPARKLE_GUTTER_ICON = getIcon("/images/codefix/sparkle.svg")
    @JvmField
    val WALKTHROUGH_LEARN_AS_YOU_CODE = getIcon("/images/walkthrough/learn.png")
    @JvmField
    val WALKTHROUGH_WELCOME = getIcon("/images/walkthrough/welcome.png")
    @JvmField
    val WALKTHROUGH_CONNECT_WITH_YOUR_TEAM = getIcon("/images/walkthrough/connect.png")
    @JvmField
    val WALKTHROUGH_REACH_OUT_TO_US = getIcon("/images/walkthrough/community.png")
    @JvmField
    val WALKTHROUGH_TOOLWINDOW_ICON = getIcon("/images/walkthrough/toolwindow_icon.svg")
    @JvmField
    val STATUS_ACCEPTED = getIcon("images/status/resolved-accepted.svg")
    @JvmField
    val STATUS_FALSE_POSITIVE = getIcon("/images/status/false-positive.svg")
    @JvmField
    val STATUS_LINK_OFF = getIcon("images/status/link_off.svg")

    private val BUG_ICONS = mapOf(
        IssueSeverity.BLOCKER to getIcon("/images/bug/bugBlocker.svg"),
        IssueSeverity.CRITICAL to getIcon("/images/bug/bugHigh.svg"),
        IssueSeverity.MAJOR to getIcon("/images/bug/bugMedium.svg"),
        IssueSeverity.MINOR to getIcon("/images/bug/bugLow.svg"),
        IssueSeverity.INFO to getIcon("/images/bug/bugInfo.svg")
    )

    private val CODE_SMELL_ICONS = mapOf(
        IssueSeverity.BLOCKER to getIcon("/images/codeSmell/codeSmellBlocker.svg"),
        IssueSeverity.CRITICAL to getIcon("/images/codeSmell/codeSmellHigh.svg"),
        IssueSeverity.MAJOR to getIcon("/images/codeSmell/codeSmellMedium.svg"),
        IssueSeverity.MINOR to getIcon("/images/codeSmell/codeSmellLow.svg"),
        IssueSeverity.INFO to getIcon("/images/codeSmell/codeSmellInfo.svg")
    )

    private val VULNERABILITY_ICONS = mapOf(
        IssueSeverity.BLOCKER to getIcon("/images/vulnerability/vulnerabilityBlocker.svg"),
        IssueSeverity.CRITICAL to getIcon("/images/vulnerability/vulnerabilityHigh.svg"),
        IssueSeverity.MAJOR to getIcon("/images/vulnerability/vulnerabilityMedium.svg"),
        IssueSeverity.MINOR to getIcon("/images/vulnerability/vulnerabilityLow.svg"),
        IssueSeverity.INFO to getIcon("/images/vulnerability/vulnerabilityInfo.svg")
    )

    private val PROBABILITY_ICONS = mapOf(
        VulnerabilityProbability.HIGH to getIcon("/images/hotspot/hotspotHigh.svg"),
        VulnerabilityProbability.MEDIUM to getIcon("/images/hotspot/hotspotMedium.svg"),
        VulnerabilityProbability.LOW to getIcon("/images/hotspot/hotspotLow.svg")
    )

    private val SEVERITY_ICONS = mapOf(
        IssueSeverity.BLOCKER to getIcon("/images/impact/blocker.svg"),
        IssueSeverity.CRITICAL to getIcon("/images/impact/high.svg"),
        IssueSeverity.MAJOR to getIcon("/images/impact/medium.svg"),
        IssueSeverity.MINOR to getIcon("/images/impact/low.svg"),
        IssueSeverity.INFO to getIcon("/images/impact/info.svg")
    )

    private val IMPACT_ICONS = mapOf(
        ImpactSeverity.BLOCKER to getIcon("/images/impact/blocker.svg"),
        ImpactSeverity.HIGH to getIcon("/images/impact/high.svg"),
        ImpactSeverity.MEDIUM to getIcon("/images/impact/medium.svg"),
        ImpactSeverity.LOW to getIcon("/images/impact/low.svg"),
        ImpactSeverity.INFO to getIcon("/images/impact/info.svg")
    )

    private val LOADING_CODEFIX_ICONS = listOf(
        getIcon("/images/codefix/loading/1.svg"),
        getIcon("/images/codefix/loading/2.svg"),
        getIcon("/images/codefix/loading/3.svg"),
        getIcon("/images/codefix/loading/4.svg"),
        getIcon("/images/codefix/loading/5.svg"),
        getIcon("/images/codefix/loading/6.svg"),
        getIcon("/images/codefix/loading/7.svg"),
        getIcon("/images/codefix/loading/8.svg"),
        getIcon("/images/codefix/loading/9.svg"),
        getIcon("/images/codefix/loading/10.svg"),
        getIcon("/images/codefix/loading/11.svg"),
        getIcon("/images/codefix/loading/12.svg"),
        getIcon("/images/codefix/loading/13.svg")
    )

    val backgroundColorsByVulnerabilityProbability = mapOf(
        VulnerabilityProbability.HIGH to JBColor(Color(254, 243, 242), Color(253, 162, 155, 30)),
        VulnerabilityProbability.MEDIUM to JBColor(Color(255, 240, 235), Color(254, 150, 75, 30)),
        VulnerabilityProbability.LOW to JBColor(Color(252, 245, 228), Color(250, 220, 121, 30))
    )

    val fontColorsByVulnerabilityProbability = mapOf(
        VulnerabilityProbability.HIGH to JBColor(Color(180, 35, 24), Color(253, 162, 155)),
        VulnerabilityProbability.MEDIUM to JBColor(Color(147, 55, 13), Color(254, 150, 75)),
        VulnerabilityProbability.LOW to JBColor(Color(140, 94, 30), Color(250, 220, 121))
    )

    val borderColorsByVulnerabilityProbability = mapOf(
        VulnerabilityProbability.HIGH to JBColor(Color(217, 44, 32), Color(253, 162, 155)),
        VulnerabilityProbability.MEDIUM to JBColor(Color(254, 150, 75), Color(254, 150, 75)),
        VulnerabilityProbability.LOW to JBColor(Color(250, 220, 121), Color(250, 220, 121))
    )

    val backgroundColorsBySeverity = mapOf(
        IssueSeverity.BLOCKER to JBColor(Color(254, 228, 226), Color(128, 27, 20, 30)),
        IssueSeverity.CRITICAL to JBColor(Color(254, 243, 242), Color(253, 162, 155, 30)),
        IssueSeverity.MAJOR to JBColor(Color(255, 240, 235), Color(254, 150, 75, 30)),
        IssueSeverity.MINOR to JBColor(Color(252, 245, 228), Color(250, 220, 121, 30)),
        IssueSeverity.INFO to JBColor(Color(245, 251, 255), Color(143, 202, 234, 30))
    )

    val fontColorsBySeverity = mapOf(
        IssueSeverity.BLOCKER to JBColor(Color(128, 27, 20), Color(249, 112, 102)),
        IssueSeverity.CRITICAL to JBColor(Color(180, 35, 24), Color(253, 162, 155)),
        IssueSeverity.MAJOR to JBColor(Color(147, 55, 13), Color(254, 150, 75)),
        IssueSeverity.MINOR to JBColor(Color(140, 94, 30), Color(250, 220, 121)),
        IssueSeverity.INFO to JBColor(Color(49, 107, 146), Color(143, 202, 234))
    )

    val borderColorsBySeverity = mapOf(
        IssueSeverity.BLOCKER to JBColor(Color(128, 27, 20), Color(249, 112, 102)),
        IssueSeverity.CRITICAL to JBColor(Color(217, 44, 32), Color(253, 162, 155)),
        IssueSeverity.MAJOR to JBColor(Color(254, 150, 75), Color(254, 150, 75)),
        IssueSeverity.MINOR to JBColor(Color(250, 220, 121), Color(250, 220, 121)),
        IssueSeverity.INFO to JBColor(Color(143, 202, 234), Color(143, 202, 234))
    )

    val backgroundColorsByImpact = mapOf(
        ImpactSeverity.BLOCKER to JBColor(Color(254, 228, 226), Color(128, 27, 20, 30)),
        ImpactSeverity.HIGH to JBColor(Color(254, 243, 242), Color(253, 162, 155, 30)),
        ImpactSeverity.MEDIUM to JBColor(Color(255, 240, 235), Color(254, 150, 75, 30)),
        ImpactSeverity.LOW to JBColor(Color(252, 245, 228), Color(250, 220, 121, 30)),
        ImpactSeverity.INFO to JBColor(Color(245, 251, 255), Color(143, 202, 234, 30))
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

    fun loadingCodeFixIcon() = AnimatedIcon(125, *LOADING_CODEFIX_ICONS.toTypedArray())

    fun severity(severity: IssueSeverity): Icon {
        return SEVERITY_ICONS[severity]!!
    }

    @JvmStatic
    fun impact(impact: ImpactSeverity): Icon {
        return IMPACT_ICONS[impact]!!
    }

    @JvmStatic
    fun riskSeverity(severity: DependencyRiskDto.Severity): Icon {
        return when (severity) {
            DependencyRiskDto.Severity.INFO -> IMPACT_ICONS[ImpactSeverity.INFO]!!
            DependencyRiskDto.Severity.LOW -> IMPACT_ICONS[ImpactSeverity.LOW]!!
            DependencyRiskDto.Severity.MEDIUM -> IMPACT_ICONS[ImpactSeverity.MEDIUM]!!
            DependencyRiskDto.Severity.HIGH -> IMPACT_ICONS[ImpactSeverity.HIGH]!!
            DependencyRiskDto.Severity.BLOCKER -> IMPACT_ICONS[ImpactSeverity.BLOCKER]!!
        }
    }

    @JvmStatic
    fun toDisabled(icon: Icon): Icon {
        return IconLoader.getDisabledIcon(icon)
    }

    @JvmStatic
    fun hotspotTypeWithProbability(vulnerabilityProbability: VulnerabilityProbability): Icon {
        return PROBABILITY_ICONS[vulnerabilityProbability]!!
    }

    @JvmStatic
    fun getIconForTypeAndSeverity(type: RuleType, severity: IssueSeverity): Icon {
        return when (type) {
            RuleType.BUG -> BUG_ICONS[severity]!!
            RuleType.CODE_SMELL -> CODE_SMELL_ICONS[severity]!!
            RuleType.VULNERABILITY -> VULNERABILITY_ICONS[severity]!!
            RuleType.SECURITY_HOTSPOT -> throw UnsupportedOperationException("Security Hotspots do not support severity")
        }
    }

}
