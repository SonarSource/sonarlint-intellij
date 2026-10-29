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
package org.sonarlint.intellij.ui.page.settings.global

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.page.settings.findTabbedPane
import org.sonarlint.intellij.ui.page.settings.selectTab
import org.sonarlint.intellij.ui.page.settings.selectedTabContent

class SonarLintGlobalSettingsPage internal constructor(
    private val project: Project,
    private val configurable: SonarLintGlobalConfigurable,
) : AutoCloseable {

    private val root: JComponent
        get() = configurable.createComponent() ?: error("SonarQube for IDE settings UI was not created")

    val settingsTab: GlobalSettingsTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_SETTINGS)
            GlobalSettingsTabPage(root.selectedTabContent())
        }

    val fileExclusionsTab: GlobalFileExclusionsTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_FILE_EXCLUSIONS)
            GlobalFileExclusionsTabPage(project, root)
        }

    val rulesTab: GlobalRulesTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_RULES)
            GlobalRulesTabPage(root.selectedTabContent())
        }

    val aboutTab: GlobalAboutTabPage
        get() = readOnEdt {
            root.findTabbedPane().selectTab(TAB_ABOUT)
            GlobalAboutTabPage(root.selectedTabContent())
        }

    /** Persists changes the same way as OK/Apply on the Settings dialog for this configurable. */
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
        private const val TAB_SETTINGS = "Settings"
        private const val TAB_FILE_EXCLUSIONS = "File Exclusions"
        private const val TAB_RULES = "Rules"
        private const val TAB_ABOUT = "About"

        /**
         * Opens the **Tools → SonarQube for IDE** application settings page (same target as
         * [ShowSettingsUtil.showSettingsDialog] with [SonarLintGlobalConfigurable]).
         */
        fun open(project: Project): SonarLintGlobalSettingsPage = readOnEdt {
            val configurable = SonarLintGlobalConfigurable()
            configurable.createComponent()
            configurable.reset()
            SonarLintGlobalSettingsPage(project, configurable)
        }
    }
}
