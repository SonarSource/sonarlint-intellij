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

import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.DocumentUtil
import java.awt.BorderLayout
import java.awt.Dimension
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_ADDITION
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_REMOVAL

class CodeFixDiffView(currentCode: String, newCode: String) :
    OnePixelSplitter(false, 0.5f, 0.01f, 0.99f), Disposable {

    private lateinit var beforeEditor: EditorEx
    private lateinit var afterEditor: EditorEx

    init {
        DocumentUtil.writeInRunUndoTransparentAction {
            beforeEditor = createCodeFixEditor() as EditorEx
            afterEditor = createCodeFixEditor() as EditorEx
            layout = BorderLayout()
            val beforeDocument: Document = beforeEditor.document
            val afterDocument: Document = afterEditor.document
            beforeDocument.replaceString(0, beforeDocument.textLength, currentCode)
            afterDocument.replaceString(0, afterDocument.textLength, newCode)
            beforeEditor.component.preferredSize = Dimension(beforeEditor.component.preferredSize.width,
                beforeEditor.lineHeight * beforeDocument.lineCount + beforeEditor.lineHeight)
            afterEditor.component.preferredSize = Dimension(afterEditor.component.preferredSize.width,
                afterEditor.lineHeight * afterDocument.lineCount + beforeEditor.lineHeight)

            val fragments = ComparisonManagerImpl.getInstanceImpl()
                .compareChars(currentCode, newCode, ComparisonPolicy.TRIM_WHITESPACES, EmptyProgressIndicator())

            fragments.forEach { fragment ->
                beforeEditor.markupModel.addRangeHighlighter(
                    DIFF_REMOVAL,
                    fragment.startOffset1,
                    fragment.endOffset1,
                    0,
                    HighlighterTargetArea.EXACT_RANGE
                )
                afterEditor.markupModel.addRangeHighlighter(
                    DIFF_ADDITION,
                    fragment.startOffset2,
                    fragment.endOffset2,
                    0,
                    HighlighterTargetArea.EXACT_RANGE
                )
            }
        }
        beforeEditor.document.setReadOnly(true)
        afterEditor.document.setReadOnly(true)

        firstComponent = beforeEditor.component
        secondComponent = afterEditor.component
    }

    private fun createCodeFixEditor(): Editor {
        val editorFactory = EditorFactory.getInstance()
        val editorDocument = editorFactory.createDocument("")
        val editor = editorFactory.createViewer(editorDocument) as EditorEx
        editor.settings.apply {
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            additionalColumnsCount = 0
            additionalLinesCount = 0
            isRightMarginShown = false
            isCaretRowShown = false
            isVirtualSpace = false
            isAdditionalPageAtBottom = false
        }
        editor.setCaretEnabled(false)
        editor.contextMenuGroupId = null
        return editor
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(beforeEditor)
        EditorFactory.getInstance().releaseEditor(afterEditor)
    }

}
