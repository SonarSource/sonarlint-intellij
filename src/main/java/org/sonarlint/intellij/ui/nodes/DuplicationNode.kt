package org.sonarlint.intellij.ui.nodes

import org.sonar.duplications.block.Block
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile

class DuplicationNode(model: DuplicationNodeModel) : AbstractNode() {

    val model: DuplicationNodeModel

    init {
        this.model = model
    }

    override fun render(renderer: TreeCellRenderer) {
        if (model.blocks.isEmpty()) return
        renderer.append("${model.file.uri()} Count: ${model.blocks.size}")
    }

    override fun getIssueCount(): Int {
        return 1
    }

}

data class DuplicationNodeModel(
    val file: ClientInputFile,
    var blocks: MutableCollection<Block> = mutableListOf()
)
