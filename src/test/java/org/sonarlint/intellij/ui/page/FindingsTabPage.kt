/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.page

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import javax.swing.tree.TreeNode
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.ui.currentfile.FindingsPanel
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.tree.IssueTree

class FindingsTabPage internal constructor(
    private val panel: FindingsPanel,
) {

    val toolbar: FindingsTabToolbarPage = FindingsTabToolbarPage(panel)

    val findingsTree: FindingsTreePage
        get() = FindingsTreePage(findingsIssueTree())

    val issues: List<FindingNodePage>
        get() = findingsTree.issues

    fun refreshForFile(file: VirtualFile) {
        panel.update(file)
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun findingsIssueTree(): IssueTree {
        val trees = panel.findComponents<IssueTree>()
        check(trees.isNotEmpty()) { "No IssueTree found under Findings tab" }
        return trees.maxByOrNull { countIssueNodes(it.model.root as TreeNode) }
            ?: error("No IssueTree found under Findings tab")
    }

    private fun countIssueNodes(node: TreeNode): Int {
        var count = if (node is IssueNode) 1 else 0
        for (i in 0 until node.childCount) {
            count += countIssueNodes(node.getChildAt(i))
        }
        return count
    }
}
