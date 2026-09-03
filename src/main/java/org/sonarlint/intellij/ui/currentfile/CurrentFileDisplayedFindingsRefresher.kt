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
package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.ui.filter.FilterCriteria
import org.sonarlint.intellij.ui.filter.FindingsFilter

/**
 * Refreshes the filtered findings snapshot read by [org.sonarlint.intellij.editor.DirectHighlighter].
 * Independent of whether the Current File panel UI is visible or has run an update cycle.
 */
@Service(Service.Level.PROJECT)
class CurrentFileDisplayedFindingsRefresher(private val project: Project) {

    fun refreshDisplayedFindings(file: VirtualFile?) {
        val criteria = getService(project, SonarLintToolWindow::class.java).getCurrentFileFilterCriteria()
            ?: FilterCriteria()
        val filteredFindings = FindingsFilter(project).filterAllFindings(file, criteria)
        getService(project, CurrentFileDisplayedFindingsStore::class.java).setSnapshot(filteredFindings)
    }
}
