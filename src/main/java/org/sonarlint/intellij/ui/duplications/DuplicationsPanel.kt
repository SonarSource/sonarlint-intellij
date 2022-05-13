package org.sonarlint.intellij.ui.duplications

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ScrollPaneFactory
import org.sonarlint.intellij.analysis.cpd.Duplication
import org.sonarlint.intellij.ui.tree.DuplicationsTree
import org.sonarlint.intellij.ui.tree.DuplicationsTreeModelBuilder

import java.awt.BorderLayout
import java.net.URI
import javax.swing.JPanel

class DuplicationsPanel(project: Project) : SimpleToolWindowPanel(false, true), DataProvider, Disposable {

    private val treeBuilder: DuplicationsTreeModelBuilder
    private val tree: DuplicationsTree

    init {
        val duplicationsPanel = JPanel(BorderLayout())
        val blaUri = URI.create("/bla/bla")
        treeBuilder = DuplicationsTreeModelBuilder()
//        val file = DefaultClientInputFile(Mock.MyVirtualFile(), blaUri.path, false, Charset.defaultCharset())
//        val mapEntry = Pair(file, DuplicationNode(DuplicationNodeModel(file, mutableListOf(Block.builder().build()))))
        tree = DuplicationsTree(project, treeBuilder.model)
        duplicationsPanel.add(ScrollPaneFactory.createScrollPane(tree))
        setContent(duplicationsPanel)
    }

    override fun getData(dataId: String): Any? {
        return null
    }

    override fun dispose() {
        // Nothing to do
    }

    fun updateContent(duplications: MutableList<Duplication>) {
        treeBuilder.updateModel(duplications)
    }

}


