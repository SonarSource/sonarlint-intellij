/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import java.util.concurrent.atomic.AtomicReference
import org.sonarlint.intellij.ui.filter.FilteredFindings

/**
 * Thread-safe snapshot of filtered findings shown in the Current File tool window tab.
 * Updated on the EDT from [CurrentFilePanel]; readable from any thread (e.g. external annotator on a background thread).
 */
@Service(Service.Level.PROJECT)
class CurrentFileDisplayedFindingsStore(project: Project) {

    private val snapshot = AtomicReference(EMPTY)

    fun setSnapshot(findings: FilteredFindings) {
        snapshot.set(findings)
    }

    fun getFindingsForFile(file: VirtualFile): FilteredFindings {
        return snapshot.get().getFindingsForFile(file)
    }

    companion object {
        private val EMPTY = FilteredFindings(emptyList(), emptyList(), emptyList(), emptyList())
    }

}
