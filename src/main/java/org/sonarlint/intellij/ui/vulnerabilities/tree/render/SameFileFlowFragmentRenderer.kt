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

import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleTextAttributes
import org.sonarlint.intellij.finding.SameFileFlowFragment
import org.sonarlint.intellij.ui.tree.NodeRenderer
import org.sonarlint.intellij.ui.tree.TreeCellRenderer

object SameFileFlowFragmentRenderer : NodeRenderer<SameFileFlowFragment> {

  override fun render(renderer: TreeCellRenderer, node: SameFileFlowFragment) {
    val file = node.file
    renderer.icon = if (file == null || !file.isValid) AllIcons.FileTypes.Unknown else file.fileType.icon
    renderer.append(file?.name ?: node.originalFileName ?: "Unknown file")
    if (file == null || !file.isValid) {
      renderer.append(" (unreachable in local code)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
    renderer.toolTipText = null
  }

}
