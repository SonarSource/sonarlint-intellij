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
package org.sonarlint.intellij.ui

import com.intellij.openapi.ui.DialogWrapper

open class ProjectSelectionDialog : DialogWrapper(true) {
    private var selectedProject: String? = null

    open fun selectProject(): String? {
        init()
        title = "Select a Project to Bind"
        show()
        return selectedProject
    }

    override fun createActions() = arrayOf(cancelAction)

    override fun createCenterPanel() = SelectProjectPanel(this)

    fun setSelectedProject(project: String) {
        selectedProject = project
        close(OK_EXIT_CODE)
    }

    override fun doCancelAction() {
        selectedProject = null
        super.doCancelAction()
    }
}
