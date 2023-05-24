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
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.nodes.AbstractNode
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import java.util.Objects

class FlowNode(val flow: Flow, private val label: String, val issue: LocalTaintVulnerability) : AbstractNode() {

  override fun render(renderer: TreeCellRenderer) {
    renderer.ipad = JBUI.insets(3, 3, 3, 3)
    renderer.append(label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
  }

  override fun equals(other: Any?): Boolean {
    return other is FlowNode && other.label == label
  }

  override fun hashCode(): Int {
    return Objects.hash(label)
  }

  override fun toString() = label
}
