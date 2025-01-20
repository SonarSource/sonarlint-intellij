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
package org.sonarlint.intellij.finding

import com.intellij.openapi.vfs.VirtualFile

class Flow(val position: Int, val locations: List<Location>) {
    val crossFileFlowFragments = run {
        var fragmentIndex = 0
        locations.foldIndexed(mutableListOf<SameFileFlowFragment>()) { index, acc, location ->
            var last = acc.lastOrNull()
            if (last == null || last.file != location.file) {
                last = SameFileFlowFragment(location.file, fragmentIndex++, location.originalFileName)
                acc.add(last)
            }
            last.locations.add(FragmentLocation(location, index + 1, this))
            acc
        }.toList()
    }

    val isCrossFileFlow = crossFileFlowFragments.size > 1
    fun hasMoreThanOneLocation() = locations.size > 1
}

data class SameFileFlowFragment(val file: VirtualFile?, val fragmentIndex: Int, val originalFileName: String?) {
    val locations: MutableList<FragmentLocation> = mutableListOf()
}

data class FragmentLocation(val location: Location, val positionInFlow: Int, val associatedFlow: Flow) {
    val range = location.range
    val message = location.message
}