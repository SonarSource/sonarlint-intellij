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
package org.sonarlint.intellij.ui.currentfile.tree

import com.intellij.openapi.vfs.VirtualFile
import javax.swing.tree.TreeModel
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.ui.currentfile.SummaryUiModel
import org.sonarlint.intellij.ui.currentfile.filter.SortMode

/**
 * Interface defining the contract for building and managing tree models for findings within a single file.
 * 
 * <h3>Design & Architecture:</h3>
 * This interface establishes a common contract for all tree model builders in the Current File panel.
 * 
 * <h3>Core Responsibilities:</h3>
 * Implementations of this interface are responsible for:
 * - Model Management:</strong> Creating and updating tree models based on finding data
 * - Sorting Support:</strong> Organizing findings according to user-selected sort criteria
 * - State Tracking:</strong> Maintaining model state and providing metadata about displayed content
 */
interface SingleFileTreeModelBuilder<T: Finding> {

    fun updateModel(file: VirtualFile?, findings: List<T>)
    fun getTreeModel(): TreeModel
    fun findFindingByKey(key: String): T?
    fun isEmpty(): Boolean
    fun numberOfDisplayedFindings(): Int
    fun setSortMode(mode: SortMode)
    fun getSummaryUiModel(): SummaryUiModel
    fun removeFinding(finding: T)

}
