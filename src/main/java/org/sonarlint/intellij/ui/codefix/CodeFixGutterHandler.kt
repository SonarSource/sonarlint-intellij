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

    private val iconHolders = mutableListOf<GutterIconHolder>()

    fun addIcons(editor: Editor, icons: Set<RangeHighlighter>) {
        iconHolders.add(GutterIconHolder(editor, icons.toMutableSet()))
    }

    fun cleanIconsFromDisposedEditorsAndSelectedEditor(editor: Editor) {
        // Iterate on disposed editors and remove the entries
        val listIterator = iconHolders.iterator()
        while (listIterator.hasNext()) {
            val item = listIterator.next()
            if (item.editor.isDisposed) {
                listIterator.remove()
            }
        }

        // For the selected editor, simply clear the icons as preparation for the new issues raised
        iconHolders.forEach { holder ->
            if (holder.editor == editor) {
                holder.clearIcons()
            }
        }
    }

}
