/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.panel
import com.intellij.util.Function
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.stream.Collectors
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class SearchProjectKeyDialog(
    parent: Component,
    private val lastSelectedProjectKey: String?,
    private val projectsByKey: Map<String, ServerProject>,
    private val isSonarCloud: Boolean
) : DialogWrapper(
    parent, false
) {
    private lateinit var projectList: JBList<ServerProject>

    init {
        title = "Search Project in " + if (isSonarCloud) "SonarCloud" else "SonarQube"
        init()
    }

    override fun createCenterPanel() = panel {
        row {
            label("Select a project from the list. Start typing to search.")
        }
        row {
            projectList = createProjectList()
            setProjectsInList(projectsByKey.values)
            scrollPane(projectList)
        }
    }

    private fun updateOk(): Boolean {
        val valid = lastSelectedProjectKey != null
        myOKAction.isEnabled = valid
        return valid
    }

    val selectedProjectKey: String?
        get() {
            val project = projectList.selectedValue
            return project?.key
        }

    private fun createProjectList(): JBList<ServerProject> {
        val projectList = JBList<ServerProject>()
        projectList.setEmptyText("No projects found in " + if (isSonarCloud) "SonarCloud" else "SonarQube")
        projectList.cellRenderer = ProjectListRenderer()
        projectList.addListSelectionListener(ProjectItemListener())
        projectList.addMouseListener(ProjectMouseListener())
        projectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        projectList.visibleRowCount = 10
        projectList.border = IdeBorderFactory.createBorder()
        ListSpeedSearch(projectList, Function { o: ServerProject? -> o!!.name + " " + o.key } as Function<ServerProject?, String>)
        return projectList
    }

    private fun setProjectsInList(projects: Collection<ServerProject>) {
        val projectComparator = java.util.Comparator { o1: ServerProject?, o2: ServerProject? ->
            val c1 = o1!!.name.compareTo(o2!!.name, ignoreCase = true)
            if (c1 != 0) {
                return@Comparator c1
            }
            o1.key.compareTo(o2.key, ignoreCase = true)
        }
        val sortedProjects = projects.stream()
            .sorted(projectComparator)
            .collect(Collectors.toList())
        var selected: ServerProject? = null
        if (lastSelectedProjectKey != null) {
            selected = sortedProjects.stream()
                .filter { project: ServerProject -> lastSelectedProjectKey == project.key }
                .findAny().orElse(null)
        }
        val projectListModel = CollectionListModel(sortedProjects)
        projectList.model = projectListModel
        projectList.cellRenderer = ProjectListRenderer()
        setSelectedProject(selected)
    }

    private fun setSelectedProject(selected: ServerProject?) {
        if (selected != null) {
            projectList.setSelectedValue(selected, true)
        } else if (!projectList.isEmpty && lastSelectedProjectKey == null) {
            projectList.setSelectedIndex(0)
        } else {
            projectList.setSelectedValue(null, true)
        }
        updateOk()
    }

    /**
     * Render projects in combo box
     */
    private class ProjectListRenderer : ColoredListCellRenderer<ServerProject>() {
        override fun customizeCellRenderer(
            list: JList<out ServerProject>,
            value: ServerProject,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
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