package org.sonarlint.intellij.ui.tree

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.TreeModel

class DuplicationsTree (private val project: Project, model: TreeModel) : Tree(model), DataProvider {


    init {
        setShowsRootHandles(false)
        setCellRenderer(TreeCellRenderer())
        expandRow(0)
    }

    override fun getData(dataId: String): Any? {
        return when {
            else -> null
        }
    }
}
