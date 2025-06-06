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

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport.TwosideSyncScrollSupport
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.DocumentUtil
import java.awt.BorderLayout
import java.awt.Dimension
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_ADDITION
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_REMOVAL

class CodeFixDiffView(project: Project, file: VirtualFile, currentCode: String, newCode: String) :
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

            val fragments = ComparisonManager.getInstance().compareChars(
                    currentCode, newCode, ComparisonPolicy.TRIM_WHITESPACES, EmptyProgressIndicator()
                )

            fragments.forEach { fragment ->
                beforeEditor.markupModel.addRangeHighlighter(
                    DIFF_REMOVAL,
                    fragment.startOffset1,
                    fragment.endOffset1,
                    0,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                afterEditor.markupModel.addRangeHighlighter(
                    DIFF_ADDITION,
                    fragment.startOffset2,
                    fragment.endOffset2,
                    0,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
            }
        }
        beforeEditor.document.setReadOnly(true)
        afterEditor.document.setReadOnly(true)

        val scheme = EditorColorsManager.getInstance().globalScheme
        beforeEditor.highlighter =
            EditorHighlighterFactory.getInstance().createEditorHighlighter(file, scheme, project)
        afterEditor.highlighter =
            EditorHighlighterFactory.getInstance().createEditorHighlighter(file, scheme, project)

        firstComponent = beforeEditor.component
        secondComponent = afterEditor.component

        synchronizeScrollbars()
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

    private fun synchronizeScrollbars() {
        val scrollable = object : BaseSyncScrollable() {
            override fun processHelper(helper: ScrollHelper) {
                if (!helper.process(0, 0)) return
                helper.process(DiffUtil.getLineCount(beforeEditor.document), DiffUtil.getLineCount(afterEditor.document))
            }

            override fun isSyncScrollEnabled(): Boolean = true
        }

        val scrollSupport = TwosideSyncScrollSupport(listOf(beforeEditor, afterEditor), scrollable)
        val listener = VisibleAreaListener { e -> scrollSupport.visibleAreaChanged(e) }

        beforeEditor.scrollingModel.addVisibleAreaListener(listener)
        afterEditor.scrollingModel.addVisibleAreaListener(listener)
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(beforeEditor)
        EditorFactory.getInstance().releaseEditor(afterEditor)
    }

}
