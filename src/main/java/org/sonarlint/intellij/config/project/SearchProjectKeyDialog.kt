/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.text.StringUtil
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
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto

class SearchProjectKeyDialog(
    parent: Component,
    private val lastSelectedProjectKey: String?,
    private val projectsByKey: Map<String, SonarProjectDto>,
    isSonarCloud: Boolean,
) : DialogWrapper(
    parent, false
) {
    private lateinit var mainPanel: JBPanel<JBPanel<*>>
    private lateinit var projectList: JBList<SonarProjectDto>
    private lateinit var searchTextField: SearchTextField

    init {
        title = "Select " + (if (isSonarCloud) "SonarCloud" else "SonarQube") + " Project To Bind"
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
        val emptyText = StringBuilder("No projects found")
        if (projectsByKey.isEmpty()) {
            emptyText.append(" for the selected connection")
        }
        projectList.setEmptyText(emptyText.toString())
        projectList.cellRenderer = ProjectListRenderer()
        projectList.addListSelectionListener(ProjectItemListener())
        projectList.addMouseListener(ProjectMouseListener())
        projectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        projectList.visibleRowCount = 10
        projectList.border = IdeBorderFactory.createBorder()
        projectList.cellRenderer = ProjectListRenderer()
        return projectList
    }

    private fun updateProjectsInList() {
        val filterText = searchTextField.text
        val selection: SonarProjectDto? = projectList.selectedValue
        val model = (projectList.model as? DefaultListModel)
        model!!.clear()
        val sortedProjects = projectsByKey.values
            .sortedWith(compareBy({ it.name.lowercase(Locale.ENGLISH) }, { it.key.lowercase(Locale.ENGLISH) }))

        var selectedIndex = -1
        var index = 0
        for (sortedProject in sortedProjects) {
            if (StringUtil.containsIgnoreCase(sortedProject.key, filterText) || StringUtil.containsIgnoreCase(
                    sortedProject.name,
                    filterText
                )
            ) {
                model.addElement(sortedProject)
                if ((selection != null && selection === sortedProject) || lastSelectedProjectKey == sortedProject.key) {
                    selectedIndex = index
                }
                index++
            }
        }
        if (!model.isEmpty) {
            if (selectedIndex >= 0) {
                projectList.selectedIndex = selectedIndex
                projectList.ensureIndexIsVisible(selectedIndex)
            } else {
                projectList.clearSelection()
            }
        }
        projectList.revalidate()
        projectList.repaint()

        updateOk()
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
