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
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import icons.SonarLintIcons
import org.sonarsource.sonarlint.core.commons.IssueSeverity
import org.sonarsource.sonarlint.core.commons.RuleType
import java.awt.FlowLayout


class RuleHeaderPanel : JBPanel<RuleHeaderPanel>(FlowLayout(FlowLayout.LEFT)) {
    private val ruleTypeIcon = JBLabel()
    private val ruleTypeLabel = JBLabel()
    private val ruleSeverityIcon = JBLabel()
    private val ruleSeverityLabel = JBLabel()
    private val ruleKeyLabel = JBLabel()


    init {
        add(ruleTypeIcon, HorizontalLayout.LEFT)
        add(ruleTypeLabel.apply {
            border = JBUI.Borders.emptyRight(10)
        }, HorizontalLayout.LEFT)
        add(ruleSeverityIcon, HorizontalLayout.LEFT)
        add(ruleSeverityLabel, HorizontalLayout.LEFT)
        add(ruleKeyLabel.apply {
            border = JBUI.Borders.emptyLeft(10)
        }, HorizontalLayout.CENTER)
    }


    fun clear() {
        ruleTypeIcon.icon = null
        ruleTypeLabel.text = ""
        ruleSeverityIcon.icon = null
        ruleSeverityLabel.text = ""
        ruleKeyLabel.text = ""
        revalidate()
    }

    fun update(ruleKey: String, type: RuleType, severity: IssueSeverity) {
        ruleTypeIcon.icon = SonarLintIcons.type(type.toString())
        ruleTypeLabel.text = clean(type.toString())
        ruleSeverityIcon.icon = SonarLintIcons.severity(severity.toString())
        ruleSeverityLabel.text = clean(severity.toString())
        ruleKeyLabel.text = ruleKey
        revalidate()
    }

    fun showMessage(msg: String) {
        clear()
        ruleTypeLabel.text = msg
        revalidate()
    }

    private fun clean(txt: String): String {
        return StringUtil.capitalize(txt.lowercase().replace("_", " "))
    }

}