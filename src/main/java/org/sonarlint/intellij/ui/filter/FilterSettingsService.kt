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
package org.sonarlint.intellij.ui.filter

import com.intellij.openapi.components.Service
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings

/**
 * Service to manage filter panel settings persistence.
 * 
 * This service handles storing and retrieving user preferences for filter panel settings
 * such as sorting mode or scope mode, ensuring they persist across IDE restarts.
 */
@Service(Service.Level.APP)
class FilterSettingsService {
    fun getDefaultSortMode(): SortMode {
        return try {
            SortMode.valueOf(getGlobalSettings().defaultSortMode)
        } catch (_: IllegalArgumentException) {
            // If an invalid sort mode is stored, default to DATE
            SortMode.DATE
        }
    }

    fun setDefaultSortMode(sortMode: SortMode) {
        setDefaultSortMode(sortMode, getGlobalSettings())
    }

    fun setDefaultSortMode(sortMode: SortMode, settings: SonarLintGlobalSettings) {
        if (settings.defaultSortMode != sortMode.name) {
            settings.defaultSortMode = sortMode.name
        }
    }

    fun getDefaultFindingsScope(): FindingsScope {
        return try {
            FindingsScope.valueOf(getGlobalSettings().defaultFindingsScope)
        } catch (_: IllegalArgumentException) {
            FindingsScope.CURRENT_FILE
        }
    }

    fun setDefaultFindingsScope(scope: FindingsScope) {
        setDefaultFindingsScope(scope, getGlobalSettings())
    }

    fun setDefaultFindingsScope(scope: FindingsScope, settings: SonarLintGlobalSettings) {
        if (settings.defaultFindingsScope != scope.name) {
            settings.defaultFindingsScope = scope.name
        }
    }
}
