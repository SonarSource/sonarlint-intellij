package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.vfs.VirtualFile
import javax.swing.tree.TreeModel
import org.sonarlint.intellij.finding.Finding

interface SingleFileTreeModelBuilder<T: Finding> {

    fun updateModel(file: VirtualFile?, findings: List<T>)
    fun refreshModel()
    fun getTreeModel(): TreeModel
    fun findFindingByKey(key: String): T?
    fun removeFinding(finding: T)
    fun clear()
    fun allowResolvedFindings(shouldIncludeResolvedFindings: Boolean)
    fun isEmpty(): Boolean
    fun numberOfDisplayedFindings(): Int

}
