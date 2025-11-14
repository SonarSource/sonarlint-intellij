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
package org.sonarlint.intellij.ui.tree

import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.TreePath
import org.sonarlint.intellij.ui.nodes.FileNode

/**
 * Utility class to snapshot and restore the expansion state of file nodes in trees.
 * This is used to preserve the user's expanded/collapsed state when trees are refreshed.
 */
object TreeExpansionStateManager {
    
    /**
     * Takes a snapshot of the expansion state of file nodes in the tree.
     * Returns a set of file paths that are currently expanded.
     */
    fun takeFileNodeExpansionStateSnapshot(tree: Tree): Set<String> {
        val root = tree.model.root ?: return emptySet()
        val rootChildCount = tree.model.getChildCount(root)
        if (rootChildCount == 0) return emptySet()

        // Pre-allocate with estimated size to reduce reallocations
        val expandedFilePaths = HashSet<String>(rootChildCount)

        for (i in 0 until rootChildCount) {
            val child = tree.model.getChild(root, i)
            if (child is FileNode) {
                val filePath = TreePath(arrayOf(root, child))
                if (tree.isExpanded(filePath)) {
                    val file = child.file()
                    if (file != null && file.isValid) {
                        expandedFilePaths.add(file.path)
                    }
                }
            }
        }
        
        return expandedFilePaths
    }
    
    /**
     * Restores the expansion state of file nodes in the tree based on a snapshot.
     * Only expands file nodes that were previously expanded.
     */
    fun restoreFileNodeExpansionState(tree: Tree, expandedFilePaths: Set<String>) {
        // Early return if nothing to restore
        if (expandedFilePaths.isEmpty()) {
            val root = tree.model.root ?: return
            // Still expand root to show the tree structure
            tree.expandPath(TreePath(root))
            return
        }
        
        val root = tree.model.root ?: return
        val rootChildCount = tree.model.getChildCount(root)
        if (rootChildCount == 0) return
        
        // Expand root first to make file nodes accessible
        tree.expandPath(TreePath(root))

        for (i in 0 until rootChildCount) {
            val child = tree.model.getChild(root, i)
            if (child is FileNode) {
                val file = child.file()
                if (file != null && file.isValid && expandedFilePaths.contains(file.path)) {
                    val filePath = TreePath(arrayOf(root, child))
                    tree.expandPath(filePath)
                }
            }
        }
    }
}

