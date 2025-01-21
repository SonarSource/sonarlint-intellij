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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBPanel
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_ADDITION
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_REMOVAL
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleFragment
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleType


class RuleCodeSnippet(private val project: Project, fileTypeFromRule: FileType, private val codeExampleFragment: CodeExampleFragment) :
    JBPanel<RuleCodeSnippet>(), Disposable {

    private var myEditor: EditorEx

    init {
        border = JBUI.Borders.empty(0, 10, 5, 10)
        myEditor = createEditor() as EditorEx
        layout = BorderLayout()
        add(myEditor.component, BorderLayout.CENTER)
        setText(codeExampleFragment.code, fileTypeFromRule)
        myEditor.document.setReadOnly(true)
        myEditor.putUserData(CODE_EXAMPLE_FRAGMENT_KEY, codeExampleFragment)
    }

    private fun createEditor(): Editor {
        val editorFactory = EditorFactory.getInstance()
        val editorDocument = editorFactory.createDocument("")
        editorDocument.putUserData(IS_SONARLINT_DOCUMENT, true)
        val editor = editorFactory.createViewer(editorDocument) as EditorEx
        val settings = editor.settings
        settings.isLineMarkerAreaShown = false
        settings.isFoldingOutlineShown = false
        settings.additionalColumnsCount = 0
        settings.additionalLinesCount = 0
        settings.isRightMarginShown = false
        settings.isCaretRowShown = false
        settings.isLineNumbersShown = false
        settings.isVirtualSpace = false
        settings.isAdditionalPageAtBottom = false
        editor.setCaretEnabled(false)
        editor.contextMenuGroupId = null

        return editor
    }

    private fun setText(text: String, fileType: FileType) {
        DocumentUtil.writeInRunUndoTransparentAction { configureByText(text, fileType) }
    }

    private fun configureByText(usageText: String, fileType: FileType) {
        val document: Document = myEditor.document
        val text = StringUtil.convertLineSeparators(usageText)
        document.replaceString(0, document.textLength, text)
        val scheme = EditorColorsManager.getInstance().globalScheme
        myEditor.highlighter =
            EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, project)

        if (codeExampleFragment.diffTarget != null) {
            val provider = DiffUtil.createTextDiffProvider(
                project, SimpleDiffRequest(
                    "Diff",
                    DiffContentFactory.getInstance().createEmpty(),
                    DiffContentFactory.getInstance().createEmpty(), null, null
                ), TextDiffSettingsHolder.TextDiffSettings(), {}, this
            )
            val fragments =
                provider.compare(codeExampleFragment.code, codeExampleFragment.diffTarget!!.code, EmptyProgressIndicator())

            val attributeKey = if (codeExampleFragment.type == CodeExampleType.Compliant) DIFF_ADDITION else DIFF_REMOVAL
            fragments?.forEach { fragment ->
                myEditor.markupModel.addRangeHighlighter(
                    attributeKey,
                    fragment.startOffset1,
                    fragment.endOffset1,
                    0,
                    HighlighterTargetArea.EXACT_RANGE
                )
            }
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(myEditor)
    }

    companion object {
        val CODE_EXAMPLE_FRAGMENT_KEY: Key<CodeExampleFragment> = Key.create("SONARLINT_CODE_EXAMPLE_FRAGMENT_KEY")
        val IS_SONARLINT_DOCUMENT: Key<Boolean> = Key.create("IS_SONARLINT_DOCUMENT")
    }
}
