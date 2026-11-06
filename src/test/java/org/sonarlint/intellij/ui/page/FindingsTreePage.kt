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

import javax.swing.tree.TreeNode
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.tree.IssueTree

class FindingsTreePage internal constructor(
    private val tree: IssueTree,
) {

    val issues: List<FindingNodePage>
        get() = collectIssueNodes(tree.model.root as TreeNode).map { node ->
            FindingNodePage(node.issue().getRuleKey(), node.issue().getMessage())
        }

    val summaryLabelText: String
        get() {
            val row = 0
            return tree.convertValueToText(
                tree.model.root,
                tree.isRowSelected(row),
                tree.isExpanded(row),
                true,
                row,
                false,
            )
        }

    private fun collectIssueNodes(node: TreeNode): List<IssueNode> {
        val result = mutableListOf<IssueNode>()
        collectIssueNodes(node, result)
        return result
    }

    private fun collectIssueNodes(node: TreeNode, result: MutableList<IssueNode>) {
        if (node is IssueNode) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            collectIssueNodes(node.getChildAt(i), result)
        }
    }
}
