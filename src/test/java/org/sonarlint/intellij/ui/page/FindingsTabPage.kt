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
package org.sonarlint.intellij.ui.page

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import org.sonarlint.intellij.ui.currentfile.FindingsPanel
import org.sonarlint.intellij.ui.currentfile.TreeType
import org.sonarlint.intellij.ui.tree.IssueTree

class FindingsTabPage internal constructor(
    private val panel: FindingsPanel,
) {

    val toolbar: FindingsTabToolbarPage = FindingsTabToolbarPage(panel)

    val findingsTree: FindingsTreePage
        get() = FindingsTreePage(findingsIssueTree())

    val oldFindingsTree: FindingsTreePage
        get() = FindingsTreePage(oldFindingsIssueTree())

    val issues: List<FindingNodePage>
        get() = findingsTree.issues + oldFindingsTree.issues

    fun refreshForFile(file: VirtualFile) {
        panel.update(file)
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun findingsIssueTree(): IssueTree =
        panel.getTree(TreeType.ISSUES, isOld = false) as IssueTree

    private fun oldFindingsIssueTree(): IssueTree =
        panel.getTree(TreeType.ISSUES, isOld = true) as IssueTree
}
