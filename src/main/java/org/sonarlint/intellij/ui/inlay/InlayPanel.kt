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
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.SwingConstants
import org.jdesktop.swingx.HorizontalLayout
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor

// Inspired from https://github.com/cursive-ide/component-inlay-example
class InlayPanel(
    private val project: Project,
    val editor: Editor,
    val startLine: Int,
    val endLine: Int,
    val newCode: String,
    val file: PsiFile,
    val index: Int,
    private val total: Int?,
) : RoundedPanelWithBackgroundColor(), Disposable {

    private val centerPanel = RoundedPanelWithBackgroundColor()
    private val inlayRef = Ref<Disposable>()
    private val toggleButton = JButton("Minimize")
    private var diffPanel: DiffRequestPanel? = null
    private var disposed = false

    init {
        layout = BorderLayout()
        centerPanel.layout = VerticalFlowLayout(0)
        val manager = InlayManager.from(editor)
        initPanel()
        updatePanelWithData()
        val inlay = manager.insertBefore(startLine, this)
        revalidate()
        inlayRef.set(inlay)
        val viewport = (editor as? EditorImpl)?.scrollPane?.viewport
        viewport?.dispatchEvent(ComponentEvent(viewport, ComponentEvent.COMPONENT_RESIZED))

        EditorUtil.disposeWithEditor(editor, this)
    }

    private fun initPanel() {
        val action = object : AnAction({ "Close" }, AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) {
                dispose()
            }
        }

        val closeButton = ActionButton(
            action,
            action.templatePresentation.clone(),
            ActionPlaces.TOOLBAR,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        )

        val closeButtonPanel = RoundedPanelWithBackgroundColor().apply {
            layout = BorderLayout()
            border = JBUI.Borders.emptyLeft(10)
            add(closeButton, BorderLayout.EAST)
        }

        toggleButton.apply {
            addActionListener {
                text = if (centerPanel.isVisible) {
                    "Expand"
                } else {
                    "Minimize"
                }
                centerPanel.isVisible = !centerPanel.isVisible
                revalidate()
                repaint()
            }
            isVisible = false
        }

        val title = if (total != null) {
            " (fix $index/$total)"
        } else {
            ""
        }

        add(RoundedPanelWithBackgroundColor().apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(5)
            add(JBLabel("SonarLint AI Suggestion$title", SonarLintIcons.SONARLINT, SwingConstants.LEFT), BorderLayout.WEST)
            add(RoundedPanelWithBackgroundColor().apply {
                layout = HorizontalLayout(5)
                add(toggleButton)
                add(closeButtonPanel)
            }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        cursor = Cursor.getDefaultCursor()
    }

    private fun updatePanelWithData() {
        val doc = editor.document
        try {
            val textRange = doc.createRangeMarker(doc.getLineStartOffset(startLine - 1), doc.getLineEndOffset(endLine - 1))
            val currentCode = doc.getText(TextRange(textRange.startOffset, textRange.endOffset))

            diffPanel = DiffManager.getInstance().createRequestPanel(
                project,
                this,
                null
            )

            diffPanel!!.setRequest(
                SimpleDiffRequest(
                    "Diff Between Code Examples",
                    DiffContentFactory.getInstance().create(currentCode),
                    DiffContentFactory.getInstance().create(newCode),
                    "Current code",
                    "Suggested code"
                )
            )

            centerPanel.add(diffPanel!!.component)

            val applyButton = JButton("Apply Changes").apply {
                foreground = JBColor.green
                font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
            }
            val declineButton = JButton("Decline Changes").apply {
                foreground = JBColor.red
                font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
            }

            applyButton.addActionListener {
                DocumentUtil.writeInRunUndoTransparentAction {
                    editor.document.replaceString(
                        textRange.startOffset,
                        textRange.endOffset,
                        normalizeLineEndingsToLineFeeds(newCode)
                    )
                    CodeStyleManager.getInstance(project).reformatText(file, textRange.startOffset, textRange.endOffset)
                }
                dispose()
            }

            declineButton.addActionListener {
                dispose()
            }

            val buttonPanel = RoundedPanelWithBackgroundColor().apply {
                layout = BorderLayout()
                add(RoundedPanelWithBackgroundColor().apply {
                    layout = HorizontalLayout(5)
                    add(applyButton)
                    add(declineButton)
                }, BorderLayout.WEST)
            }

            val southPanel = RoundedPanelWithBackgroundColor().apply {
                layout = VerticalFlowLayout(5)
                add(buttonPanel)
            }

            add(southPanel, BorderLayout.SOUTH)
        } catch (e: IndexOutOfBoundsException) {
            SonarLintConsole.get(project).error("Fix is invalid", e)
            get(project).simpleNotification(
                null,
                "Fix suggestion is invalid",
                NotificationType.WARNING
            )
        }
    }

    private fun normalizeLineEndingsToLineFeeds(text: String) = StringUtil.convertLineSeparators(text)

    override fun dispose() {
        runOnUiThread(project) {
            inlayRef.get()?.dispose()
            diffPanel?.dispose()
            disposed = true
        }
    }

}
