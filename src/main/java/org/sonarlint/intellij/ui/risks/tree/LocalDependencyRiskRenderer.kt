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
package org.sonarlint.intellij.ui.risks.tree

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleTextAttributes
import javax.swing.Icon
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.icons.DisplayedStatus
import org.sonarlint.intellij.ui.icons.FindingIconBuilder
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.tree.NodeRenderer
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

object LocalDependencyRiskRenderer : NodeRenderer<LocalDependencyRisk> {

    override fun render(renderer: TreeCellRenderer, node: LocalDependencyRisk) {
        val quality = node.quality
        val severity = node.severity
        val impactText = StringUtil.capitalize(severity.toString().lowercase())
        val qualityText = quality.toString().lowercase()
        val toolTipText = "$impactText $qualityText"
        val displayedStatus = DisplayedStatus.fromFinding(node)
        val undecoratedIcon = FindingIconBuilder.forBaseIcon(SonarLintIcons.riskSeverity(severity))
            .withDisplayedStatus(displayedStatus)
            .undecorated()
            .build()
        setIcon(renderer, undecoratedIcon)

        renderer.setIconToolTip(toolTipText)
        renderer.toolTipText = "Double-click to open in the browser"

        val text = "${node.packageName}:${node.packageVersion}"
        if (displayedStatus == DisplayedStatus.OPEN) {
            renderer.append(text)
        } else {
            renderer.append(text, SimpleTextAttributes.GRAY_ATTRIBUTES)
        }

        val details = getDetails(node)
        renderer.append(details, SimpleTextAttributes.GRAY_ATTRIBUTES)
    }

    private fun getDetails(node: LocalDependencyRisk): String {
        var details = " -"
        if (node.vulnerabilityId != null && node.cvssScore != null) {
            details += " [${node.cvssScore}] ${node.vulnerabilityId}"
        }
        details = if (node.type == DependencyRiskDto.Type.PROHIBITED_LICENSE) {
            "$details (Prohibited License)"
        } else {
            "$details (Vulnerability)"
        }
        return details
    }

    private fun setIcon(renderer: TreeCellRenderer, icon: Icon) {
        renderer.icon = icon
    }
}
