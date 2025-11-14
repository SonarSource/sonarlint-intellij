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
package org.sonarlint.intellij.ui.vulnerabilities.tree.render

import com.intellij.ui.SimpleTextAttributes
import org.sonarlint.intellij.ui.nodes.AbstractNode.spaceAndThinSpace
import org.sonarlint.intellij.ui.tree.NodeRenderer
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import org.sonarlint.intellij.ui.vulnerabilities.tree.FileSummary

object FileRenderer : NodeRenderer<FileSummary> {

    override fun render(renderer: TreeCellRenderer, node: FileSummary) {
        renderer.icon = node.file.fileType.icon
        renderer.setIconToolTip(node.file.fileType.displayName + " file")
        renderer.append(node.file.name)
        renderer.append(spaceAndThinSpace() + "(" + node.findingType.display(node.findingsCount) + ")",
            SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
        renderer.toolTipText = "Double click to list file " + node.findingType.displayLabel(node.findingsCount)
    }
}
