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
package org.sonarlint.intellij.editor

import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.editor.EditorHighlightRefresh.Companion.ALL_OPEN_FILES
import org.sonarlint.intellij.editor.EditorHighlightRefresh.Companion.NONE

/**
 * Describes whether - and for which files - SonarQube editor highlights should be recomputed after on-the-fly
 * findings change.
 *
 * This replaces the previous `(refreshEditorHighlights, highlightChangedFiles, highlightAllOpenFiles)` boolean triplet
 * that was threaded through several layers. The concrete set of files is resolved by [org.sonarlint.intellij.analysis.OnTheFlyFindingsCoordinator],
 * not the Current File panel, and [changedFiles] is not gated by findings scope:
 *  - [enabled] `false` ([NONE]): editor highlights are left untouched (e.g. for intermediate analysis results).
 *  - [allOpenFiles] `true` ([ALL_OPEN_FILES]): every open editor is refreshed (e.g. when all findings are cleared).
 *  - [changedFiles]: files whose findings changed; honored whenever present. When null, every open editor is refreshed.
 */
data class EditorHighlightRefresh(
    val enabled: Boolean,
    val changedFiles: Collection<VirtualFile>? = null,
    val allOpenFiles: Boolean = false,
) {
    companion object {
        /** Leave editor highlights untouched. */
        @JvmField
        val NONE = EditorHighlightRefresh(enabled = false)

        /** Refresh highlights for every currently open editor. */
        @JvmField
        val ALL_OPEN_FILES = EditorHighlightRefresh(enabled = true, allOpenFiles = true)

        /** Refresh highlights, resolving the affected files from [changedFiles] when present, otherwise all open editors. */
        @JvmStatic
        @JvmOverloads
        fun enabled(changedFiles: Collection<VirtualFile>? = null) =
            EditorHighlightRefresh(enabled = true, changedFiles = changedFiles)
    }
}
