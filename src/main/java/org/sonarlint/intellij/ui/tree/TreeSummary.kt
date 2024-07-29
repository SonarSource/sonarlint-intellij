/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.project.Project
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.cayc.NewCodePeriodCache
import org.sonarlint.intellij.common.util.SonarLintUtils.getService

class TreeSummary(private val project: Project, private val treeContentKind: TreeContentKind, private val holdsOldFindings: Boolean) {
    private var emptyText = DEFAULT_EMPTY_TEXT
    var text: String = emptyText
        private set

    fun refresh(filesCount: Int, findingsCount: Int) {
        val newCodePeriod = getService(project, NewCodePeriodCache::class.java).periodAsString
        emptyText = computeEmptyText(newCodePeriod)
        text = computeText(filesCount, findingsCount, newCodePeriod)
    }

    fun reset() {
        emptyText = DEFAULT_EMPTY_TEXT
        text = emptyText
    }

    private fun computeText(filesCount: Int, findingsCount: Int, newCodePeriod: String): String {
        if (findingsCount == 0) {
            return emptyText
        }

        var sinceText = ""
        var newOrOldOrNothing = ""
        if (isFocusOnNewCode()) {
            sinceText = if (holdsOldFindings) "" else " $newCodePeriod"
            newOrOldOrNothing = if (holdsOldFindings) "older " else "new "
        }

        return FORMAT.format(
            findingsCount,
            newOrOldOrNothing,
            pluralize(treeContentKind.displayName, findingsCount),
            filesCount,
            pluralize("file", filesCount),
            sinceText
        )
    }

    private fun computeEmptyText(newCodePeriod: String): String {
        if (isFocusOnNewCode()) {
            return if (holdsOldFindings) {
                "No older ${treeContentKind.displayName}s"
            } else {
                "No new ${treeContentKind.displayName}s $newCodePeriod"
            }
        }
        return "No ${treeContentKind.displayName}s to display"
    }

    private fun isFocusOnNewCode() = getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode(project)

    companion object {
        private const val DEFAULT_EMPTY_TEXT = "No analysis done"
        private const val FORMAT = "Found %d %s%s in %d %s%s"

        private fun pluralize(word: String, count: Int): String {
            return if (count == 1) word else word + "s"
        }
    }
}
