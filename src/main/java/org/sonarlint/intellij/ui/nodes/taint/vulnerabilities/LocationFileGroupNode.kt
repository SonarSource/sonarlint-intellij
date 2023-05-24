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

import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleTextAttributes
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.nodes.AbstractNode
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import java.util.Objects

class LocationFileGroupNode(private val position: Int, private val location: Location, val issue: LocalTaintVulnerability) : AbstractNode() {
  fun file() = location.file

  fun icon() = if (location.file == null || !location.file.isValid) AllIcons.FileTypes.Unknown else location.file.fileType.icon

  override fun render(renderer: TreeCellRenderer) {
    renderer.icon = icon()
    val file = file()
    renderer.append(file?.name ?: location.originalFileName ?: "Unknown file")
    if (file == null || !file.isValid) {
      renderer.append(" (unreachable in local code)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
  }

  override fun equals(other: Any?): Boolean {
    return other is LocationFileGroupNode && other.location.file == location.file
  }

  override fun hashCode(): Int {
    return Objects.hash(position, location.file)
  }

  override fun toString() = location.file?.name ?: location.originalFileName ?: "Unknown file"

}
