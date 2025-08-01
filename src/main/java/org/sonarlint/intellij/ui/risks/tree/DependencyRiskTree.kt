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
package org.sonarlint.intellij.ui.risks.tree

import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.ui.PopupHandler
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.annotations.NonNls
import org.sonarlint.intellij.actions.ChangeDependencyRiskStatusAction
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.tree.TreeCellRenderer
import org.sonarlint.intellij.util.DataKeys.Companion.DEPENDENCY_RISK_DATA_KEY

class DependencyRiskTree(updater: DependencyRiskTreeUpdater) : Tree(updater.model), DataProvider {

    init {
        setShowsRootHandles(false)
        setCellRenderer(TreeCellRenderer(updater.renderer))
        expandRow(0)
        val group = DefaultActionGroup()
        group.add(ChangeDependencyRiskStatusAction())
        PopupHandler.installPopupMenu(this, group, ActionPlaces.TODO_VIEW_POPUP)
    }

    override fun getData(dataId: @NonNls String): Any? {
        return when {
            PlatformDataKeys.TREE_EXPANDER.`is`(dataId) -> DefaultTreeExpander(this)
            DEPENDENCY_RISK_DATA_KEY.`is`(dataId) -> getSelectedDependencyRisk()
            else -> null
        }
    }

    fun getSelectedDependencyRisk(): LocalDependencyRisk? {
        val node = getSelectedNode()
        return node as? LocalDependencyRisk
    }

    private fun getSelectedNode(): Any? {
        return selectionPath?.lastPathComponent
    }

}
