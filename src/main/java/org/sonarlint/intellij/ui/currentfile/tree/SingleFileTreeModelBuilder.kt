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
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.ui.currentfile.SummaryUiModel
import org.sonarlint.intellij.ui.filter.FilterSettingsService
import org.sonarlint.intellij.ui.filter.SortMode

/**
 * Abstract base class for building and managing tree models for findings within a single file.
 * 
 * <h3>Design & Architecture:</h3>
 * This abstract class provides the foundation for all tree model builders in the Current File panel,
 * including built-in performance optimization with change detection.
 * 
 * <h3>Core Responsibilities:</h3>
 * - Model Management: Creating and updating tree models based on finding data
 * - Sorting Support: Organizing findings according to user-selected sort criteria  
 * - State Tracking: Maintaining model state and providing metadata about displayed content
 * - Performance Optimization: Avoiding unnecessary tree rebuilds through change detection
 */
abstract class SingleFileTreeModelBuilder<T: Finding> {

    protected var sortMode: SortMode = getService(FilterSettingsService::class.java).getDefaultSortMode()

    // Track changes to avoid unnecessary tree rebuilds
    private var lastFindings: List<T> = emptyList()
    private var lastSortMode: SortMode? = null
    private var lastShowFileNames: Boolean? = null

    fun updateModel(file: VirtualFile?, findings: List<T>) {
        updateModelWithScope(file, findings, false)
    }

    fun updateModelWithScope(file: VirtualFile?, findings: List<T>, showFileNames: Boolean) {
        // Performance optimization: Skip expensive operations if nothing has changed
        if (findings == lastFindings && 
            sortMode == lastSortMode && 
            showFileNames == lastShowFileNames) {
            return
        }
        
        // Call the concrete implementation
        performUpdateModelWithScope(file, findings, showFileNames)
        
        // Cache values for next comparison
        lastFindings = findings.toList()
        lastSortMode = sortMode
        lastShowFileNames = showFileNames
    }

    fun changeSortMode(mode: SortMode) {
        if (sortMode != mode) {
            sortMode = mode
            // Clear cache to force tree rebuild with new sort order
            lastSortMode = null
        }
    }

    open fun setScopeSuffix(suffix: String) {
        // Default implementation - do nothing
    }

    abstract fun getTreeModel(): TreeModel
    abstract fun findFindingByKey(key: String): T?
    abstract fun isEmpty(): Boolean
    abstract fun numberOfDisplayedFindings(): Int
    abstract fun getSummaryUiModel(): SummaryUiModel
    abstract fun removeFinding(finding: T)

    /**
     * Abstract method that concrete classes must implement to perform the actual tree model update.
     * This method is only called when a change has been detected.
     */
    protected abstract fun performUpdateModelWithScope(file: VirtualFile?, findings: List<T>, showFileNames: Boolean)

}
