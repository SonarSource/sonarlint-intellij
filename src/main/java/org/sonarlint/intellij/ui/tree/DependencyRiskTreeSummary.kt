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
package org.sonarlint.intellij.ui.tree

class DependencyRiskTreeSummary(private val treeContentKind: TreeContentKind) : TreeSummary {

    private var emptyText = DEFAULT_EMPTY_TEXT
    private var text: String = emptyText

    override fun getText() = text

    override fun refresh(filesCount: Int, findingsCount: Int) {
        emptyText = computeEmptyText()
        text = computeText(findingsCount)
    }

    override fun reset() {
        emptyText = DEFAULT_EMPTY_TEXT
        text = emptyText
    }

    private fun computeText(findingsCount: Int): String {
        if (findingsCount == 0) {
            return emptyText
        }

        return FORMAT.format(findingsCount, pluralize(treeContentKind.displayName, findingsCount))
    }

    private fun computeEmptyText(): String {
        return "No ${treeContentKind.displayName}s to display"
    }

    companion object {
        private const val DEFAULT_EMPTY_TEXT = "No dependency risks found"
        private const val FORMAT = "Found %d %s"

        private fun pluralize(word: String, count: Int): String {
            return if (count == 1) word else word + "s"
        }
    }

}
