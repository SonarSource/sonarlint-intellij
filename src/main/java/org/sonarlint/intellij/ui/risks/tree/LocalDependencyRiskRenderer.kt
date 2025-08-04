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
package org.sonarlint.intellij.ui.risks.tree

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.scale.JBUIScale
import javax.swing.Icon
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.tree.NodeRenderer
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import org.sonarlint.intellij.util.CompoundIcon

object LocalDependencyRiskRenderer : NodeRenderer<LocalDependencyRisk> {

    override fun render(renderer: TreeCellRenderer, node: LocalDependencyRisk) {
        val gap = if (JBUIScale.isUsrHiDPI) 8 else 4

        val quality = node.quality
        val severity = node.severity
        val impactText = StringUtil.capitalize(severity.toString().lowercase())
        val qualityText = quality.toString().lowercase()
        val toolTipText = "$impactText $qualityText"
        setIcon(
            renderer,
            CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, SonarLintIcons.riskSeverity(severity))
        )

        renderer.setIconToolTip(toolTipText)
        renderer.toolTipText = "Click to open in the browser"
        renderer.append("${node.packageName} ${node.packageVersion}")
    }

    private fun setIcon(renderer: TreeCellRenderer, icon: Icon) {
        renderer.icon = icon
    }

}
