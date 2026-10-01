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
package org.sonarlint.intellij.config.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto

private const val NO_PROJECTS_FOUND = "No projects found"

class SearchProjectKeyDialog(
    parent: Component,
    lastSelectedProjectKey: String?,
    private val projectsByKey: Map<String, SonarProjectDto>,
    private val connection: ServerConnection,
) : DialogWrapper(
    parent, false
) {
    private val logger = Logger.getInstance(SearchProjectKeyDialog::class.java)
    private lateinit var mainPanel: JBPanel<JBPanel<*>>
    private lateinit var projectList: JBList<SonarProjectDto>
    private lateinit var searchTextField: SearchTextField
    private var searchGeneration = 0
    private var disposed = false
    private var rememberedSelectedProjectKey = lastSelectedProjectKey
    private val initialProjects = projectsByKey.values
        .sortedWith(compareBy({ it.name.lowercase(Locale.ENGLISH) }, { it.key.lowercase(Locale.ENGLISH) }))
    private val searchTimer = Timer(300) {
        if (disposed || searchTextField.text.isBlank()) {
            return@Timer
        }
        val generation = searchGeneration
        getService(BackendService::class.java).fuzzySearchProjects(connection, searchTextField.text.trim())
            .whenComplete { response, error ->
                runOnEdt {
                    if (disposed || generation != searchGeneration) {
                        return@runOnEdt
                    }
                    if (error == null) {
                        projectList.setEmptyText(NO_PROJECTS_FOUND)
                        showProjects(response.topResults)
                    } else {
                        logger.warn("Could not search projects for connection ${connection.name}", error)
                        projectList.setEmptyText("Could not search projects. Check the connection and try again.")
                        showProjects(emptyList())
                    }
                }
            }
    }.apply { isRepeats = false }

    init {
        title = "Select " + (if (connection.isSonarCloud) "SonarQube Cloud" else "SonarQube Server") + " Project To Bind"
        init()
    }

    override fun createCenterPanel(): JComponent {
        mainPanel = JBPanel<JBPanel<*>>(VerticalFlowLayout())

        searchTextField = SearchTextField()
        searchTextField.textEditor.emptyText.text = "Search by project key or name"
        searchTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateProjectsInList()
            }
        })
        searchTextField.textEditor.columns(COLUMNS_MEDIUM)

        projectList = createProjectList()
        updateProjectsInList()

        mainPanel.add(searchTextField)
        mainPanel.add(JBScrollPane(projectList))

        return mainPanel
    }

    private fun updateOk(): Boolean {
        val valid = selectedProjectKey != null
        myOKAction.isEnabled = valid
        return valid
    }

    val selectedProjectKey: String?
        get() {
            val project = projectList.selectedValue
            return project?.key
        }

    private fun createProjectList(): JBList<SonarProjectDto> {
        val projectList = JBList<SonarProjectDto>(DefaultListModel())
        projectList.cellRenderer = ProjectListRenderer()
        projectList.addListSelectionListener(ProjectItemListener())
        projectList.addMouseListener(ProjectMouseListener())
        projectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        projectList.visibleRowCount = 10
        projectList.border = IdeBorderFactory.createBorder()
        return projectList
    }

    private fun runOnEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }

    private fun updateProjectsInList() {
        searchGeneration++
        searchTimer.stop()
        if (searchTextField.text.isBlank()) {
            projectList.setEmptyText(
                if (projectsByKey.isEmpty()) "$NO_PROJECTS_FOUND for the selected connection" else NO_PROJECTS_FOUND
            )
            showProjects(initialProjects)
        } else {
            projectList.setEmptyText("Searching projects...")
            showProjects(emptyList())
            searchTimer.restart()
        }
    }

    private fun showProjects(projects: List<SonarProjectDto>) {
        val model = projectList.model as DefaultListModel<SonarProjectDto>
        model.clear()
        projects.forEach(model::addElement)

        val selectedIndex = projects.indexOfFirst { it.key == rememberedSelectedProjectKey }
        if (selectedIndex >= 0) {
            projectList.selectedIndex = selectedIndex
            projectList.ensureIndexIsVisible(selectedIndex)
        } else {
            projectList.clearSelection()
        }
        projectList.revalidate()
        projectList.repaint()
        updateOk()
    }

    public override fun dispose() {
        disposed = true
        searchGeneration++
        searchTimer.stop()
        super.dispose()
    }

    private class ProjectListRenderer : ColoredListCellRenderer<SonarProjectDto>() {
        override fun customizeCellRenderer(
            list: JList<out SonarProjectDto>,
            value: SonarProjectDto,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            val attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            append(value.name, attrs, true)
            // it is not working: appendTextPadding
            append(" ")
            if (index >= 0) {
                append("(" + value.key + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, false)
            }
        }
    }

    private inner class ProjectItemListener : ListSelectionListener {
        override fun valueChanged(event: ListSelectionEvent) {
            projectList.selectedValue?.key?.let { rememberedSelectedProjectKey = it }
            updateOk()
        }
    }

    private inner class ProjectMouseListener : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2 && updateOk()) {
                super@SearchProjectKeyDialog.doOKAction()
            }
        }
    }
}
