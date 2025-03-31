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
package org.sonarlint.intellij.ui.codefix

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter

data class GutterIconHolder(
    val editor: Editor,
    val icons: MutableSet<RangeHighlighter>
) {
    fun clearIcons() {
        icons.forEach { editor.markupModel.removeHighlighter(it) }
        icons.clear()
    }
}

@Service(Service.Level.PROJECT)
class CodeFixGutterHandler {

    private val iconTaintHolders = mutableListOf<GutterIconHolder>()
    private val iconIssueHolders = mutableListOf<GutterIconHolder>()

    fun addTaintIcons(editor: Editor, icons: Set<RangeHighlighter>) {
        iconTaintHolders.add(GutterIconHolder(editor, icons.toMutableSet()))
    }

    fun addIssueIcons(editor: Editor, icons: Set<RangeHighlighter>) {
        iconIssueHolders.add(GutterIconHolder(editor, icons.toMutableSet()))
    }

    fun cleanTaintIconsFromDisposedEditorsAndSelectedEditor(editor: Editor) {
        cleanIconsFromDisposedEditorsAndSelectedEditor(iconTaintHolders, editor)
    }

    fun cleanIssueIconsFromDisposedEditorsAndSelectedEditor(editor: Editor) {
        cleanIconsFromDisposedEditorsAndSelectedEditor(iconIssueHolders, editor)
    }

    private fun cleanIconsFromDisposedEditorsAndSelectedEditor(holders: MutableList<GutterIconHolder>, editor: Editor) {
        // Iterate on disposed editors and remove the entries
        val listIterator = holders.iterator()
        while (listIterator.hasNext()) {
            val item = listIterator.next()
            if (item.editor.isDisposed) {
                listIterator.remove()
            }
        }

        // For the selected editor, simply clear the icons as preparation for the new issues raised
        holders.forEach { holder ->
            if (holder.editor == editor) {
                holder.clearIcons()
            }
        }
    }

}
