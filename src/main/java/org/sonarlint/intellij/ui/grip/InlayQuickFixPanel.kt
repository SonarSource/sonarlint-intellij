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
package org.sonarlint.intellij.ui.grip

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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.ProgressBarLoadingDecorator
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.event.ComponentEvent
import java.net.URI
import java.util.UUID
import javax.swing.JButton
import javax.swing.SwingConstants
import org.jdesktop.swingx.HorizontalLayout
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestionReviewStatus

// Inspired from https://github.com/cursive-ide/component-inlay-example
class InlayQuickFixPanel(
    private val project: Project,
    val editor: Editor,
    val inlayLine: Int,
    val issueId: UUID,
    val file: VirtualFile,
    val correlationId: UUID?,
    val index: Int,
    private val total: Int?,
) : RoundedPanelWithBackgroundColor(), Disposable {

    private val centerPanel = RoundedPanelWithBackgroundColor()
    private val inlayRef = Ref<Disposable>()
    private val toggleButton = JButton("Minimize")
    private var diffPanel: DiffRequestPanel? = null
    var psiFile: PsiFile? = null
    var newCode: String? = null
    var startLine: Int? = null
    var endLine: Int? = null
    var disposed = false

    init {
        layout = BorderLayout()
        centerPanel.layout = VerticalFlowLayout(0)
        val manager = EditorComponentInlaysManager.from(editor)
        initLoadingPanel()
        val inlay = manager.insertBefore(inlayLine, this)
        revalidate()
        inlayRef.set(inlay)
        val viewport = (editor as? EditorImpl)?.scrollPane?.viewport
        viewport?.dispatchEvent(ComponentEvent(viewport, ComponentEvent.COMPONENT_RESIZED))

        EditorUtil.disposeWithEditor(editor, this)
    }

    private fun initLoadingPanel() {
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

        val loadingPanel = RoundedPanelWithBackgroundColor()
        val loadingDecorator = ProgressBarLoadingDecorator(loadingPanel, this, 150)
        loadingDecorator.startLoading(false)
        centerPanel.add(loadingDecorator.component)

        val title = if (total != null) {
            " (fix ${index + 1}/$total)"
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

    fun updatePanelWithData(
        file: PsiFile,
        newCode: String,
        startLine: Int,
        endLine: Int,
    ) {
        this.psiFile = file
        this.newCode = newCode
        this.startLine = startLine
        this.endLine = endLine
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

            toggleButton.isVisible = true

            centerPanel.removeAll()
            centerPanel.add(diffPanel!!.component)
            revalidate()
            repaint()

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
                val inlayHolder = getService(project, InlayHolder::class.java)
                inlayHolder.updateStatusInlayPanel(AiFindingState.ACCEPTED, issueId, this)
                val status = inlayHolder.getInlayData(issueId)?.getStatus()
                if (correlationId != null && status != null) {
                    sendFeedbackToBackend(
                        correlationId,
                        status
                    )
                }
                dispose()
            }

            declineButton.addActionListener {
                val inlayHolder = getService(project, InlayHolder::class.java)
                inlayHolder.updateStatusInlayPanel(AiFindingState.DECLINED, issueId, this)
                val status = inlayHolder.getInlayData(issueId)?.getStatus()
                if (correlationId != null && status != null) {
                    sendFeedbackToBackend(
                        correlationId,
                        status
                    )
                }
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
            get(project).simpleNotification(
                null,
                "Fix became invalid. Please refresh the AI suggestions.",
                NotificationType.WARNING
            )
        }
    }

    private fun sendFeedbackToBackend(
        correlationId: UUID,
        status: SuggestionReviewStatus,
    ) {
        val serviceUri = URI(getGlobalSettings().gripUrl)
        val serviceAuthToken = getGlobalSettings().gripAuthToken
        val promptVersion = getGlobalSettings().gripPromptVersion
        getService(BackendService::class.java).provideFeedback(
            serviceUri,
            serviceAuthToken,
            promptVersion,
            correlationId,
            status,
            null,
            null
        )
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
