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
import javax.swing.JComponent
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.page.settings.findTabbedPane
import org.sonarlint.intellij.ui.page.settings.selectTab
import org.sonarlint.intellij.ui.page.settings.selectedTabContent

class SonarLintProjectSettingsPage internal constructor(
    private val project: Project,
    private val configurable: SonarLintProjectConfigurable,
) : AutoCloseable {

    private val root: JComponent
        get() = configurable.createComponent() ?: error("Project Settings UI was not created")

    val bindTab: ProjectBindTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_BIND)
            ProjectBindTabPage(root.selectedTabContent())
        }

    val fileExclusionsTab: ProjectFileExclusionsTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_FILE_EXCLUSIONS)
            ProjectFileExclusionsTabPage(project, root)
        }

    val analysisPropertiesTab: ProjectAnalysisPropertiesTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_ANALYSIS_PROPERTIES)
            ProjectAnalysisPropertiesTabPage(root.selectedTabContent())
        }

    val supportedLanguagesTab: ProjectSupportedLanguagesTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_SUPPORTED_LANGUAGES)
            ProjectSupportedLanguagesTabPage(root.selectedTabContent())
        }

    fun save() {
        readOnEdt {
            if (configurable.isModified) {
                configurable.apply()
            }
        }
    }

    override fun close() {
        readOnEdt { configurable.disposeUIResources() }
    }

    companion object {
        private const val TAB_BIND = "Bind to SonarQube (Server, Cloud)"
        private const val TAB_FILE_EXCLUSIONS = "File Exclusions"
        private const val TAB_ANALYSIS_PROPERTIES = "Analysis Properties"
        private const val TAB_SUPPORTED_LANGUAGES = "Supported Languages & Analyzers"

        /** Opens **SonarQube for IDE → Project Settings** (same target as the Findings toolbar configure action). */
        fun open(project: Project): SonarLintProjectSettingsPage = readOnEdt {
            val configurable = SonarLintProjectConfigurable(project)
            configurable.createComponent()
            configurable.reset()
            repeat(100) { pumpEdt() }
            SonarLintProjectSettingsPage(project, configurable)
        }
    }
}
