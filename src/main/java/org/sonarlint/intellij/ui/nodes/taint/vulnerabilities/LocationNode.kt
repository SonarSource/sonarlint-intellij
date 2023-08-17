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
package org.sonarlint.intellij.ui.nodes.taint.vulnerabilities

import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import org.sonarlint.intellij.finding.Flow
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.nodes.AbstractNode
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import java.util.Objects

class LocationNode(private val number: Int?, val location: Location, val associatedFlow: Flow, val issue: LocalTaintVulnerability) : AbstractNode() {
  override fun render(renderer: TreeCellRenderer) {
    renderer.ipad = JBUI.insets(3)
    renderer.border = null
    if (number != null) {
      renderer.append("$number: ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }
    renderer.append(issueCoordinates(), SimpleTextAttributes.GRAY_ATTRIBUTES)
    renderer.append(" ")
    val message = location.message
    if (!message.isNullOrEmpty() && "..." != message) {
      renderer.append(message, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    if (!location.exists()) {
      renderer.append(" (unreachable in local code)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    else if (!location.codeMatches()) {
      renderer.append(" (local code not matching)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    renderer.toolTipText = "Double click to open location"
  }

  private fun issueCoordinates(): String {
    if (!location.exists()) {
      return "(-, -) "
    }
    val rangeMarker = location.range!!
    val doc = rangeMarker.document
    val line = doc.getLineNumber(rangeMarker.startOffset)
    val offset = rangeMarker.startOffset - doc.getLineStartOffset(line)
    return String.format("(%d, %d) ", line + 1, offset)
  }

  override fun equals(other: Any?): Boolean {
    return other is LocationNode
      && other.location.range?.startOffset == location.range?.startOffset
      && other.location.range?.endOffset == location.range?.endOffset
      && other.location.message == location.message
  }

  override fun hashCode(): Int {
    return Objects.hash(number, location.range?.startOffset, location.range?.endOffset, location.message)
  }

  override fun toString() = location.message ?: ""
}
