package org.sonarlint.intellij.ui.tree

import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.analysis.cpd.Duplication
import org.sonarlint.intellij.ui.nodes.DuplicationNode
import org.sonarlint.intellij.ui.nodes.DuplicationNodeModel
import org.sonarlint.intellij.ui.nodes.FileNode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile
import javax.swing.tree.DefaultTreeModel

class DuplicationsTreeModelBuilder {

    var index: MutableMap<ClientInputFile, DuplicationNode> = mutableMapOf()
    private val summary = SummaryNode()
    val model = DefaultTreeModel(summary)

    fun updateModel(duplications: MutableList<Duplication>) {
        val map = duplications.associate {
            it.occurrences[0].inputFile to DuplicationNode(
                DuplicationNodeModel(it.occurrences[0].inputFile, it.occurrences.map { o -> o.block }.toMutableList())
            )
        }
        index.keys
            .filter { !map.containsKey(it) }
            .forEach { index.remove(it) }
        for ((key, value) in map) {
            setFileDuplications(key, value)
        }

    }


    private fun removeDuplication(file: ClientInputFile) {
        val node = index[file]
        if (node != null) {
            index.remove(file)
            model.removeNodeFromParent(node)
        }
    }

    //
    private fun setFileDuplications(file: ClientInputFile, duplications: DuplicationNode) {
        if (duplications.model.blocks.isEmpty()) {
            removeDuplication(file)
            return
        }
        var newFile = false
        var dNode = index[file]
        if (dNode == null) {
            newFile = true
            dNode = duplications
            index[file] = dNode
        }
        dNode.removeAllChildren()

        if (newFile) {
            val parent = summary
            val vFile = (file as DefaultClientInputFile).clientObject
            val fileNode = FileNode(vFile)
            fileNode.add(duplications)
            val idx = parent.insertFileNode(fileNode, TaintVulnerabilityTreeModelBuilder.FileNodeComparator())
            val newIdx = intArrayOf(idx)
            model.nodesWereInserted(parent, newIdx)
        }
        model.reload()
    }
//
//    private fun addIssuesNode(dNode: DuplicationNodeModel, duplications: Iterable<DuplicationNodeModel>) {
//
//    }

}
