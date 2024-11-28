/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.SwingConstants
import org.jdesktop.swingx.HorizontalLayout
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.fix.FixSuggestionSnippet
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus

class FixSuggestionInlayPanel(
    private val project: Project,
    private val suggestion: FixSuggestionSnippet,
    val editor: Editor,
    val file: PsiFile,
    private val textRange: RangeMarker,
) : RoundedPanelWithBackgroundColor(), Disposable {

    private val titlePanel = RoundedPanelWithBackgroundColor()
    private val centerPanel = RoundedPanelWithBackgroundColor()
    private val actionPanel = RoundedPanelWithBackgroundColor()
    private val explanationPanel = RoundedPanelWithBackgroundColor()
    private val inlayRef = Ref<Disposable>()
    private var diffPanel: DiffRequestPanel? = null

    init {
        initPanels()

        val manager = InlayManager.from(editor)
        val inlay = manager.insertBefore(suggestion.startLine, this)
        revalidate()
        inlayRef.set(inlay)
        val viewport = (editor as? EditorImpl)?.scrollPane?.viewport
        viewport?.dispatchEvent(ComponentEvent(viewport, ComponentEvent.COMPONENT_RESIZED))
        EditorUtil.disposeWithEditor(editor, this)
    }

    private fun initPanels() {
        initTitlePanel()
        initCenterDiffPanel()
        initBottomPanel()

        layout = BorderLayout()
        centerPanel.layout = VerticalFlowLayout(0)

        add(titlePanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(actionPanel, BorderLayout.SOUTH)
        cursor = Cursor.getDefaultCursor()
    }

    private fun initTitlePanel() {
        val closeAction = object : AnAction({ "Close" }, AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) {
                dispose()
            }
        }

        val closeButton = ActionButton(
            closeAction,
            closeAction.templatePresentation.clone(),
            ActionPlaces.TOOLBAR,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        )

        val toggleButton = JButton(AllIcons.Actions.Collapseall).apply {
            preferredSize = Dimension(30, 30)
            addActionListener {
                runOnUiThread(project) {
                    icon = if (centerPanel.isVisible) {
                        AllIcons.Actions.Expandall
                    } else {
                        AllIcons.Actions.Collapseall
                    }
                    centerPanel.isVisible = !centerPanel.isVisible
                    revalidate()
                    repaint()
                }
            }
        }

        val titleRightSidePanel = RoundedPanelWithBackgroundColor().apply {
            layout = HorizontalLayout(5)
            border = JBUI.Borders.emptyLeft(5)
            add(toggleButton)
            add(closeButton)
        }

        titlePanel.apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(5)
            add(
                JBLabel(
                    "SonarQube for IDE Fix Suggestion (fix ${suggestion.snippetIndex}/${suggestion.totalSnippets})",
                    SonarLintIcons.SONARQUBE_FOR_INTELLIJ, SwingConstants.LEFT
                ), BorderLayout.WEST
            )
            add(titleRightSidePanel, BorderLayout.EAST)
        }
    }

    private fun initCenterDiffPanel() {
        diffPanel = DiffManager.getInstance().createRequestPanel(
            project,
            this,
            null
        )

        diffPanel!!.setRequest(
            SimpleDiffRequest(
                "Diff Between Code Examples",
                DiffContentFactory.getInstance().create(suggestion.currentCode),
                DiffContentFactory.getInstance().create(suggestion.newCode),
                "Current code",
                "Suggested code"
            )
        )

        centerPanel.add(diffPanel!!.component)
    }

    private fun initBottomPanel() {
        explanationPanel.apply {
            layout = BorderLayout()
            add(SwingHelper.createHtmlViewer(true, null, null, null).apply {
                text = suggestion.explanation
            }, BorderLayout.CENTER)
            isVisible = false
        }

        val applyButton = JButton("Apply").apply {
            foreground = JBColor.green
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        }
        val declineButton = JButton("Decline").apply {
            foreground = JBColor.red
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        }
        val showExplanation = JButton("Show Explanation").apply {
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        }

        applyButton.addActionListener {
            acceptFix()
        }

        declineButton.addActionListener {
            declineFix()
        }

        showExplanation.apply {
            addActionListener {
                runOnUiThread(project) {
                    text = if (explanationPanel.isVisible) {
                        "Show Explanation"
                    } else {
                        "Hide Explanation"
                    }
                    explanationPanel.isVisible = !explanationPanel.isVisible
                    revalidate()
                    repaint()
                }
            }
        }

        val buttonPanel = RoundedPanelWithBackgroundColor().apply {
            layout = BorderLayout()
            add(RoundedPanelWithBackgroundColor().apply {
                layout = HorizontalLayout(5)
                add(applyButton)
                add(declineButton)
            }, BorderLayout.WEST)
            add(RoundedPanelWithBackgroundColor().apply {
                layout = HorizontalLayout(5)
                add(showExplanation)
            }, BorderLayout.EAST)
        }

        actionPanel.apply {
            layout = VerticalFlowLayout(5)
            add(buttonPanel)
            add(explanationPanel)
        }
    }

    private fun declineFix() {
        getService(SonarLintTelemetry::class.java).fixSuggestionResolved(
            suggestion.suggestionId,
            FixSuggestionStatus.DECLINED,
            suggestion.snippetIndex
        )
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
        getService(SonarLintTelemetry::class.java).fixSuggestionResolved(
            suggestion.suggestionId,
            FixSuggestionStatus.ACCEPTED,
            suggestion.snippetIndex
        )
        dispose()
    }

    private fun normalizeLineEndingsToLineFeeds(text: String) = StringUtil.convertLineSeparators(text)

    override fun dispose() {
        runOnUiThread(project) {
            inlayRef.get()?.dispose()
            diffPanel?.dispose()
        }
    }

}
