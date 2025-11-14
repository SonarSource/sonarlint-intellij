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
package org.sonarlint.intellij.ui.inlay

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.JBPanel
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentEvent
import javax.swing.JButton
import org.jdesktop.swingx.HorizontalLayout
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.fix.FixSuggestionSnippet
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.codefix.CodeFixDiffView
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus

class FixSuggestionInlayPanel(
    private val project: Project,
    private val suggestion: FixSuggestionSnippet,
    private val editor: Editor,
    private val file: PsiFile,
    private val textRange: RangeMarker
) : RoundedPanelWithBackgroundColor(), Disposable {

    private val centerPanel = RoundedPanelWithBackgroundColor()
    private val actionPanel = RoundedPanelWithBackgroundColor()
    private val inlayRef = Ref<Disposable>()

    init {
        initPanels()

        val manager = InlayManager.from(editor)
        val inlay = manager.insertBefore(textRange.startOffset, this)
        revalidate()
        inlayRef.set(inlay)
        val viewport = (editor as? EditorImpl)?.scrollPane?.viewport
        viewport?.dispatchEvent(ComponentEvent(viewport, ComponentEvent.COMPONENT_RESIZED))
        EditorUtil.disposeWithEditor(editor, this)
    }

    private fun initPanels() {
        initCenterDiffPanel()
        initBottomPanel()

        layout = BorderLayout()
        centerPanel.layout = VerticalFlowLayout(0)

        add(centerPanel, BorderLayout.CENTER)
        add(actionPanel, BorderLayout.SOUTH)
        cursor = Cursor.getDefaultCursor()
    }

    private fun initCenterDiffPanel() {
        val splitter = CodeFixDiffView(project, file.virtualFile, suggestion.currentCode, suggestion.newCode).apply { border = JBUI.Borders.empty(5, 5, 0, 5) }
        centerPanel.add(splitter)
    }

    private fun initBottomPanel() {
        val applyButton = JButton("Apply").apply {
            isOpaque = false
            ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
            addActionListener { acceptFix() }
        }
        val declineButton = JButton("Decline").apply {
            isOpaque = false
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
            addActionListener { declineFix() }
        }
        val upButton = JButton(AllIcons.General.ArrowUp).apply {
            preferredSize = Dimension(35, height)
            isOpaque = false
            addActionListener {
                navigateToPreviousInlay()
            }
        }
        val downButton = JButton(AllIcons.General.ArrowDown).apply {
            preferredSize = Dimension(35, height)
            isOpaque = false
            addActionListener {
                navigateToNextInlay()
            }
        }

        val buttonPanel = RoundedPanelWithBackgroundColor().apply {
            layout = BorderLayout()
            if (suggestion.totalSnippets > 1) {
                add(JBPanel<JBPanel<*>>().apply {
                    layout = HorizontalLayout(5)
                    add(upButton)
                    add(downButton)
                }, BorderLayout.WEST)
            }
            add(RoundedPanelWithBackgroundColor().apply {
                layout = HorizontalLayout(5)
                add(applyButton)
                add(declineButton)
            }, BorderLayout.EAST)
        }

        actionPanel.apply {
            layout = VerticalFlowLayout(5)
            add(buttonPanel)
        }
    }

    private fun navigateToPreviousInlay() {
        getService(project, FixSuggestionInlayHolder::class.java).getPreviousInlay(suggestion.suggestionId, suggestion.snippetIndex - 1)?.let {
            val startLine = editor.document.getLineNumber(it.textRange.startOffset)
            val descriptor = OpenFileDescriptor(project, file.virtualFile, startLine, -1)
            runOnUiThread(project, ModalityState.defaultModalityState()) {
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
            }
        }
    }

    private fun navigateToNextInlay() {
        getService(project, FixSuggestionInlayHolder::class.java).getNextInlay(suggestion.suggestionId, suggestion.snippetIndex - 1)?.let {
            val startLine = editor.document.getLineNumber(it.textRange.startOffset)
            val descriptor = OpenFileDescriptor(project, file.virtualFile, startLine, -1)
            runOnUiThread(project, ModalityState.defaultModalityState()) {
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
            }
        }
    }

    private fun declineFix() {
        getService(project, FixSuggestionInlayHolder::class.java).removeSnippet(suggestion.suggestionId, suggestion.snippetIndex - 1)
        getService(SonarLintTelemetry::class.java).fixSuggestionResolved(
            suggestion.suggestionId,
            FixSuggestionStatus.DECLINED,
            suggestion.snippetIndex
        )
        navigateToPreviousInlay()
        dispose()
    }

    private fun acceptFix() {
        DocumentUtil.writeInRunUndoTransparentAction {
            editor.document.replaceString(
                textRange.startOffset,
                textRange.endOffset,
                normalizeLineEndingsToLineFeeds(suggestion.newCode)
            )
            CodeStyleManager.getInstance(project).reformatText(file, textRange.startOffset, textRange.endOffset)
        }
        getService(project, FixSuggestionInlayHolder::class.java).removeSnippet(suggestion.suggestionId, suggestion.snippetIndex - 1)
        getService(SonarLintTelemetry::class.java).fixSuggestionResolved(
            suggestion.suggestionId,
            FixSuggestionStatus.ACCEPTED,
            suggestion.snippetIndex
        )
        navigateToNextInlay()
        dispose()
    }

    private fun normalizeLineEndingsToLineFeeds(text: String) = StringUtil.convertLineSeparators(text)

    override fun dispose() {
        runOnUiThread(project) {
            getService(project, FixSuggestionInlayHolder::class.java).removeSnippet(suggestion.suggestionId, suggestion.snippetIndex - 1)
            inlayRef.get()?.dispose()
        }
    }

}
