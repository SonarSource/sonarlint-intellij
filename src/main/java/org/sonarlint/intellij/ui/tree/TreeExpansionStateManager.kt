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
     * Returns a set of file paths that are currently collapsed.
     * Files not in this set (expanded or new) will be expanded by default.
     */
    fun takeFileNodeExpansionStateSnapshot(tree: Tree): Set<String> {
        val root = tree.model.root ?: return emptySet()
        val rootChildCount = tree.model.getChildCount(root)
        if (rootChildCount == 0) return emptySet()

        val collapsedFilePaths = HashSet<String>()

        for (i in 0 until rootChildCount) {
            val child = tree.model.getChild(root, i)
            if (child is FileNode) {
                val filePath = TreePath(arrayOf(root, child))
                // Track only collapsed nodes - expanded and new nodes will be expanded by default
                if (!tree.isExpanded(filePath)) {
                    val file = child.file()
                    if (file != null && file.isValid) {
                        collapsedFilePaths.add(file.path)
                    }
                }
            }
        }
        
        return collapsedFilePaths
    }
    
    /**
     * Restores the expansion state of file nodes in the tree based on a snapshot.
     * Assumes the tree is already fully expanded. Collapses only the file nodes
     * that were previously collapsed (those in the snapshot).
     */
    fun restoreFileNodeExpansionState(tree: Tree, collapsedFilePaths: Set<String>) {
        if (collapsedFilePaths.isEmpty()) return
        
        val root = tree.model.root ?: return
        val rootChildCount = tree.model.getChildCount(root)
        if (rootChildCount == 0) return

        // Collapse file nodes that were previously collapsed
        for (i in 0 until rootChildCount) {
            val child = tree.model.getChild(root, i)
            if (child is FileNode) {
                val file = child.file()
                if (file != null && file.isValid && collapsedFilePaths.contains(file.path)) {
                    val filePath = TreePath(arrayOf(root, child))
                    tree.collapsePath(filePath)
                }
            }
        }
    }
}

