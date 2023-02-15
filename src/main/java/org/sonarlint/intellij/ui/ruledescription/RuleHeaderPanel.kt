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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import icons.SonarLintIcons
import org.sonarsource.sonarlint.core.commons.IssueSeverity
import org.sonarsource.sonarlint.core.commons.RuleType
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.SwingConstants


class RuleHeaderPanel : JBPanel<RuleHeaderPanel>(FlowLayout(FlowLayout.LEFT)) {
    private val ruleTypeIcon = JBLabel()
    private val ruleTypeLabel = JBLabel()
    private val ruleSeverityIcon = JBLabel()
    private val ruleSeverityLabel = JBLabel()
    private val hotspotVulnerabilityLabel = JBLabel("Review Priority: ")
    private val hotspotVulnerabilityValueLabel = JBLabel()
    private val ruleKeyLabel = JBLabel()

    // Same colors and opacity as in the icons SVG
    private val highColor = Color(0x99e05555.toInt(), true)
    private val mediumColor = Color(0x99f26522.toInt(), true)
    private val lowColor = Color(0x99f4af3d.toInt(), true)
    private val colorsByProbability = mapOf(
        VulnerabilityProbability.HIGH to JBColor(highColor, highColor),
        VulnerabilityProbability.MEDIUM to JBColor(mediumColor, mediumColor),
        VulnerabilityProbability.LOW to JBColor(lowColor, lowColor)
    )

    init {
        add(ruleTypeIcon, HorizontalLayout.LEFT)
        add(ruleTypeLabel.apply {
            border = JBUI.Borders.emptyRight(10)
        }, HorizontalLayout.LEFT)
        add(ruleSeverityIcon, HorizontalLayout.LEFT)
        add(ruleSeverityLabel, HorizontalLayout.LEFT)
        add(hotspotVulnerabilityLabel, HorizontalLayout.LEFT)
        add(hotspotVulnerabilityValueLabel.apply {
            // Don't use JBColor.WHITE as it will be dark gray on dark theme
            foreground = JBColor(Color.white, Color.white)
            font = JBFont.label().asBold()
            verticalTextPosition = SwingConstants.TOP
            isOpaque = true
            border = BorderFactory.createEmptyBorder(0, 15, 0, 15)
        }, HorizontalLayout.LEFT)
        add(ruleKeyLabel.apply {
            border = JBUI.Borders.emptyLeft(10)
        }, HorizontalLayout.CENTER)
    }

    fun clear() {
        ruleTypeIcon.icon = null
        ruleTypeLabel.text = ""
        ruleKeyLabel.text = ""
        ruleSeverityIcon.icon = null
        ruleSeverityLabel.text = ""
        hotspotVulnerabilityLabel.isVisible = false
        hotspotVulnerabilityValueLabel.text = ""
    }

    fun update(ruleKey: String, type: RuleType, severity: IssueSeverity) {
        clear()
        updateCommonFields(type, ruleKey)
        ruleSeverityIcon.icon = SonarLintIcons.severity(severity)
        ruleSeverityLabel.text = clean(severity.toString())
    }

    fun update(ruleKey: String, type: RuleType, vulnerabilityProbability: VulnerabilityProbability) {
        clear()
        updateCommonFields(type, ruleKey)
        hotspotVulnerabilityLabel.isVisible = true
        hotspotVulnerabilityValueLabel.apply {
            text = vulnerabilityProbability.name
            background = colorsByProbability[vulnerabilityProbability]
        }
    }

    private fun updateCommonFields(type: RuleType, ruleKey: String) {
        ruleTypeIcon.icon = SonarLintIcons.type(type)
        ruleTypeLabel.text = clean(type.toString())
        ruleKeyLabel.text = ruleKey
    }


    fun showMessage(msg: String) {
        clear()
        ruleTypeLabel.text = msg
    }

    private fun clean(txt: String): String {
        return StringUtil.capitalizeWords(txt.lowercase().replace("_", " "), true)
    }

}
