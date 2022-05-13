package org.sonarlint.intellij.ui.nodes

import icons.SonarLintIcons
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
        renderer.icon = SonarLintIcons.WARN
        renderer.append("Number of duplicates for the file: ${model.blocks.size}")
    }

    override fun getIssueCount(): Int {
        return 1
    }

}

data class DuplicationNodeModel(
    val file: ClientInputFile,
    var blocks: MutableCollection<Block> = mutableListOf()
)
