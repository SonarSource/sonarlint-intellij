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
package org.sonarlint.intellij.ui.report

import java.util.UUID

/**
 * Tracks which issue nodes are "checked" in the Report tab trees.
 * Scoped to one ReportPanel instance (not a service).
 */
class FindingSelectionManager {

    private val selectedIds: MutableSet<UUID> = mutableSetOf()
    private val listeners: MutableList<() -> Unit> = mutableListOf()

    fun toggle(id: UUID) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        fireListeners()
    }

    fun isSelected(id: UUID): Boolean = selectedIds.contains(id)

    fun getSelectedCount(): Int = selectedIds.size

    fun getSelectedIds(): Set<UUID> = selectedIds.toSet()

    fun clear() {
        selectedIds.clear()
        fireListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun fireListeners() {
        listeners.forEach { it() }
    }
}
