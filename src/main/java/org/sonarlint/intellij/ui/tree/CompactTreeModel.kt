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
package org.sonarlint.intellij.ui.tree

import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import org.sonarlint.intellij.ui.nodes.SummaryNode

class CompactTreeModel(private val rootNode: SummaryNode) : TreeModel {

    private val listeners = mutableListOf<TreeModelListener>()

    private var compactTree: CompactTree = CompactTree(mapOf(rootNode to emptyList()))

    fun setCompactTree(compactTree: CompactTree) {
        this.compactTree = compactTree
        notifyListeners()
    }

    override fun getRoot() = rootNode

    override fun isLeaf(node: Any) = getChildCount(node) == 0

    override fun getChild(parent: Any, index: Int) = compactTree.getChild(parent, index)

    override fun getChildCount(parent: Any) = compactTree.getChildCount(parent)

    fun getCountForType(type: Class<out Any>) = compactTree.getCountForType(type)

    override fun getIndexOfChild(parent: Any?, child: Any?) = compactTree.getIndexOfChild(parent, child)

    override fun addTreeModelListener(listener: TreeModelListener) {
        listeners.add(listener)
    }

    override fun removeTreeModelListener(listener: TreeModelListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.treeStructureChanged(TreeModelEvent(this, TreePath(root))) }
    }

    override fun valueForPathChanged(path: TreePath?, newValue: Any?) {
        throw UnsupportedOperationException("Tree is not mutable")
    }
}
