/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_ADDITION
import org.sonarlint.intellij.config.SonarLintTextAttributes.DIFF_REMOVAL
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleFragment
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleType
import org.sonarlint.intellij.util.runOnPooledThread


class RuleCodeSnippet(private val project: Project, fileTypeFromRule: FileType, private val codeExampleFragment: CodeExampleFragment) :
    JBPanel<RuleCodeSnippet>(), Disposable {

    private var myEditor: EditorEx

    init {
        border = JBUI.Borders.empty(0, 10, 5, 10)
        myEditor = createEditor(fileTypeFromRule, codeExampleFragment.code) as EditorEx
        layout = BorderLayout()
        add(myEditor.component, BorderLayout.CENTER)
        myEditor.document.setReadOnly(true)
        myEditor.putUserData(CODE_EXAMPLE_FRAGMENT_KEY, codeExampleFragment)

        // If diff highlighting is needed, do it asynchronously to avoid EDT freeze
        if (codeExampleFragment.diffTarget != null) {
            computeDiffHighlightingAsync()
        }
    }

    private fun createEditor(fileType: FileType, initialText: String): Editor {
        val editorFactory = EditorFactory.getInstance()
        // Create document with initial text to avoid a write action after editor creation
        val editorDocument = editorFactory.createDocument(StringUtil.convertLineSeparators(initialText))
        editorDocument.putUserData(IS_SONARLINT_DOCUMENT, true)
        val editor = editorFactory.createViewer(editorDocument) as EditorEx
        editor.settings.apply {
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            additionalColumnsCount = 0
            additionalLinesCount = 0
            isRightMarginShown = false
            isCaretRowShown = false
            isLineNumbersShown = false
            isVirtualSpace = false
            isAdditionalPageAtBottom = false
        }
        editor.setCaretEnabled(false)
        editor.contextMenuGroupId = null

        // Configure highlighter without write action
        val scheme = EditorColorsManager.getInstance().globalScheme
        editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, project)

        return editor
    }

    private fun computeDiffHighlightingAsync() {
        // Move expensive diff computation to background thread
        runOnPooledThread(project) {
            val provider = DiffUtil.createTextDiffProvider(
                project, SimpleDiffRequest(
                    "Diff",
                    DiffContentFactory.getInstance().createEmpty(),
                    DiffContentFactory.getInstance().createEmpty(), null, null
                ), TextDiffSettingsHolder.TextDiffSettings(), {}, this@RuleCodeSnippet
            )
            val fragments =
                provider.compare(codeExampleFragment.code, codeExampleFragment.diffTarget!!.code, EmptyProgressIndicator())

            // Apply highlighting on EDT
            runOnUiThread(project) {
                if (!project.isDisposed && !myEditor.isDisposed) {
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
