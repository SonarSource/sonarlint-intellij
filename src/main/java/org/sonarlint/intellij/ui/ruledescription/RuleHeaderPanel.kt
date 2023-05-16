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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.sonarlint.intellij.SonarLintIcons
import org.sonarsource.sonarlint.core.commons.IssueSeverity
import org.sonarsource.sonarlint.core.commons.RuleType
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.SwingConstants


class RuleHeaderPanel : JBPanel<RuleHeaderPanel>(FlowLayout(FlowLayout.LEFT)) {
    private val ruleTypeIcon = JBLabel()
    private val ruleTypeLabel = JBLabel()
    private val ruleSeverityIcon = JBLabel()
    private val ruleSeverityLabel = JBLabel()
    private val hotspotVulnerabilityLabel = JBLabel("Review priority: ")
    private val hotspotVulnerabilityValueLabel = JBLabel()
    private val ruleKeyLabel = JBLabel()

    init {
        add(ruleTypeIcon)
        add(ruleTypeLabel.apply {
            border = JBUI.Borders.emptyRight(10)
        })
        add(ruleSeverityIcon)
        add(ruleSeverityLabel)
        add(hotspotVulnerabilityLabel)
        add(hotspotVulnerabilityValueLabel.apply {
            font = JBFont.label().asBold()
            verticalTextPosition = SwingConstants.CENTER
            isOpaque = true
            border = BorderFactory.createEmptyBorder(0, 15, 0, 15)
        })
        add(ruleKeyLabel.apply {
            border = JBUI.Borders.emptyLeft(10)
        })
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
        ruleSeverityLabel.setCopyable(true)
    }

    fun update(ruleKey: String, type: RuleType, vulnerabilityProbability: VulnerabilityProbability) {
        clear()
        updateCommonFields(type, ruleKey)
        hotspotVulnerabilityLabel.isVisible = true
        hotspotVulnerabilityValueLabel.apply {
            text = vulnerabilityProbability.name
            setCopyable(true)
            background = SonarLintIcons.colorsByProbability[vulnerabilityProbability]
        }
    }

    private fun updateCommonFields(type: RuleType, ruleKey: String) {
        ruleTypeIcon.icon = SonarLintIcons.type(type)
        ruleTypeLabel.text = clean(type.toString())
        ruleTypeLabel.setCopyable(true)
        ruleKeyLabel.text = ruleKey
        ruleKeyLabel.setCopyable(true)
    }


    fun showMessage(msg: String) {
        clear()
        ruleTypeLabel.text = msg
    }

    private fun clean(txt: String): String {
        return StringUtil.capitalizeWords(txt.lowercase().replace("_", " "), true)
    }

}
