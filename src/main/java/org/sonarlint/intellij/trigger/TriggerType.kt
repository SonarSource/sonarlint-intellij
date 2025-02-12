/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.trigger

enum class TriggerType(private val displayName: String, private val shouldUpdateServerIssues: Boolean) {

    EDITOR_OPEN("Editor open", true),
    SELECTION_CHANGED("Selection changed", true),
    CURRENT_FILE_ACTION("Current file action", true),
    RIGHT_CLICK("Right click", true),
    ALL("All files", false),
    CHANGED_FILES("Changed files", false),
    COMPILATION("Compilation", false),
    EDITOR_CHANGE("Editor change", false),
    CHECK_IN("Pre-commit check", true),
    CONFIG_CHANGE("Config change", true),
    BINDING_UPDATE("Binding update", true),
    OPEN_FINDING("Open finding", true),
    SERVER_SENT_EVENT("Server-sent event", false);

    companion object {
        // Forced analysis that will appear in the report tab
        val analysisSnapshot = listOf(RIGHT_CLICK, ALL, CHANGED_FILES)

        // Non snapshot do not include CHECK_IN analysis and OPEN_FINDING analysis, as they are specific cases
        val nonAnalysisSnapshot =
            listOf(EDITOR_OPEN, CURRENT_FILE_ACTION, COMPILATION, EDITOR_CHANGE, CONFIG_CHANGE, BINDING_UPDATE, SERVER_SENT_EVENT, SELECTION_CHANGED)

        // Events that should reset the cache of the focused files already analyzed
        val shouldResetDirtyFiles = listOf(COMPILATION, CONFIG_CHANGE, BINDING_UPDATE, SERVER_SENT_EVENT)
    }

    fun getName() = displayName

    fun isShouldUpdateServerIssues() = shouldUpdateServerIssues

}
