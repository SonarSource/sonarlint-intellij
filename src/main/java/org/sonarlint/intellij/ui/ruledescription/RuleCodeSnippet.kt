/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBPanel
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout

class RuleCodeSnippet(private val project: Project) : JBPanel<RuleCodeSnippet>(), Disposable {

    private var myEditor: EditorEx

    init {
        border = JBUI.Borders.empty(0, 10, 5, 10)
        myEditor = createEditor() as EditorEx
        layout = BorderLayout()
        add(myEditor.component, BorderLayout.CENTER)
    }

    private fun createEditor(): Editor {
        val editorFactory = EditorFactory.getInstance()
        val editorDocument = editorFactory.createDocument("")
        val editor = editorFactory.createViewer(editorDocument) as EditorEx
        val scheme = EditorColorsManager.getInstance().globalScheme
        editor.colorsScheme = scheme
        val settings = editor.settings
        settings.isLineMarkerAreaShown = false
        settings.isFoldingOutlineShown = false
        settings.additionalColumnsCount = 0
        settings.additionalLinesCount = 0
        settings.isRightMarginShown = false
        settings.isCaretRowShown = false
        settings.isLineNumbersShown = false
        editor.setCaretEnabled(false)
        editor.contextMenuGroupId = null

        return editor
    }

    fun reset(usageText: String, fileType: FileType) {
        reInitViews()
        ApplicationManager.getApplication().invokeLater {
            if (myEditor.isDisposed) {
                return@invokeLater
            }
            DocumentUtil.writeInRunUndoTransparentAction { configureByText(usageText, fileType) }
        }
    }

    private fun configureByText(usageText: String, fileType: FileType) {
        val document: Document = myEditor.document
        val text = StringUtil.convertLineSeparators(usageText)
        document.replaceString(0, document.textLength, text)
        val scheme = EditorColorsManager.getInstance().globalScheme
        myEditor.highlighter =
            EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, project)
    }

    override fun dispose() {
        val editorFactory = EditorFactory.getInstance()
        editorFactory.releaseEditor(myEditor)
    }

    private fun reInitViews() {
        myEditor.reinitSettings()
        myEditor.markupModel.removeAllHighlighters()
    }
}
