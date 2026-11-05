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
import org.sonarlint.intellij.config.project.AddEditExclusionDialog
import org.sonarlint.intellij.config.project.ExclusionItem
import org.sonarlint.intellij.config.project.ExclusionTable
import org.sonarlint.intellij.config.project.ProjectExclusionsPanel
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable
import org.sonarlint.intellij.config.project.SonarLintProjectSettingsPanel
import org.sonarlint.intellij.fixtures.readOnEdt

/** Page object for the project **File Exclusions** tab. */
class ProjectFileExclusionsTabPage internal constructor(
    private val project: Project,
    private val configurable: SonarLintProjectConfigurable,
) {

    /**
     * Opens **Add SonarQube for IDE File Exclusion**.
     * Unit-test mode rejects [com.intellij.openapi.ui.DialogWrapper.showAndGet], so the dialog content is driven directly
     * and [AddProjectFileExclusionDialogPage.confirm] writes the exclusion into the table.
     */
    fun clickAddExclusion(): AddProjectFileExclusionDialogPage = readOnEdt {
        AddProjectFileExclusionDialogPage(AddEditExclusionDialog(project), ::appendExclusion)
    }

    /** Adds a file exclusion by relative path (same flow as Standalone UI IT). */
    fun addFileExclusion(relativePath: String) {
        clickAddExclusion().apply {
            selectFile()
            enterFilePath(relativePath)
            confirm()
        }
    }

    private fun appendExclusion(item: ExclusionItem) {
        val settingsPanel = configurable.declaredField("panel").get(configurable) as SonarLintProjectSettingsPanel
        val exclusionsPanel = settingsPanel.declaredField("exclusionsPanel").get(settingsPanel) as ProjectExclusionsPanel
        val table = exclusionsPanel.declaredField("table").get(exclusionsPanel) as ExclusionTable
        table.set(table.get() + item)
    }

    private fun Any.declaredField(name: String) =
        javaClass.getDeclaredField(name).also { it.isAccessible = true }
}
