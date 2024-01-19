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
package org.sonarlint.intellij.ui.vulnerabilities.tree.render

import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.finding.FragmentLocation
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.ui.tree.NodeRenderer
import org.sonarlint.intellij.ui.tree.TreeCellRenderer

object LocationRenderer : NodeRenderer<FragmentLocation> {
  override fun render(renderer: TreeCellRenderer, node: FragmentLocation) {
    renderer.ipad = JBUI.insets(3)
    renderer.border = null
    if (node.positionInFlow != null) {
      renderer.append("${node.positionInFlow}: ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }
    renderer.append(issueCoordinates(node.location), SimpleTextAttributes.GRAY_ATTRIBUTES)
    renderer.append(" ")
    val message = node.message
    if (!message.isNullOrEmpty() && "..." != message) {
      renderer.append(message, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    if (!node.location.exists()) {
      renderer.append(" (unreachable in local code)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    else if (!node.location.codeMatches()) {
      renderer.append(" (local code not matching)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    renderer.toolTipText = "Double click to open location"
  }

  private fun issueCoordinates(location: Location): String {
    if (!location.exists()) {
      return "(-, -) "
    }

    return location.file?.let {
      computeReadActionSafely(it) {
        val rangeMarker = location.range!!
        val doc = rangeMarker.document
        val line = doc.getLineNumber(rangeMarker.startOffset)
        val offset = rangeMarker.startOffset - doc.getLineStartOffset(line)
        String.format("(%d, %d) ", line + 1, offset)
      }
    } ?: "(-, -) "
  }
}
