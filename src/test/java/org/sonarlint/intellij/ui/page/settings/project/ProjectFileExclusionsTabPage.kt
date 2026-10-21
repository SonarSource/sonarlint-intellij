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
package org.sonarlint.intellij.ui.page.settings.project

import com.intellij.openapi.project.Project
import java.awt.Component
import org.sonarlint.intellij.ui.page.settings.openModalDialog
import org.sonarlint.intellij.ui.page.settings.triggerExclusionsAddAction

/** Page object for the project **File Exclusions** tab. */
class ProjectFileExclusionsTabPage internal constructor(
    private val project: Project,
    private val settingsRoot: Component,
) {

    /** Clicks the toolbar **Add** button and returns the add-exclusion dialog page object. */
    fun clickAddExclusion(): AddProjectFileExclusionDialogPage {
        val session = openModalDialog(
            openModalAction = { settingsRoot.triggerExclusionsAddAction(project) },
            dialogTitleContains = ADD_DIALOG_TITLE,
        )
        return AddProjectFileExclusionDialogPage(session.dialog, session)
    }

    /** Adds a file exclusion by relative path (same flow as Standalone UI IT). */
    fun addFileExclusion(relativePath: String) {
        clickAddExclusion().apply {
            selectFile()
            enterPath(relativePath)
            confirm()
        }
    }

    companion object {
        private const val ADD_DIALOG_TITLE = "Add SonarQube for IDE File Exclusion"
    }
}
