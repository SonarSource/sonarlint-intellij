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

import java.awt.Component
import org.sonarlint.intellij.config.component.EditableList
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents

/** Page object for the global **File Exclusions** tab. */
class GlobalFileExclusionsTabPage internal constructor(
    private val settingsRoot: Component,
) {

    /**
     * Adds a glob exclusion pattern on the File Exclusions tab.
     * Unit-test mode does not show [com.intellij.openapi.ui.Messages.showInputDialog]; the list is updated directly.
     */
    fun addExclusionPattern(pattern: String) {
        readOnEdt {
            globalExclusionsEditableList().add(pattern)
        }
    }

    private fun globalExclusionsEditableList(): EditableList<String> {
        val exclusionsPanel = settingsRoot.walkComponents()
            .first { it.javaClass.simpleName == "GlobalExclusionsPanel" }
        val listField = exclusionsPanel.javaClass.getDeclaredField("list")
        listField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return listField.get(exclusionsPanel) as EditableList<String>
    }
}
