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

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.Gray
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.fix.ShowFixSuggestion
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayHolder
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.getDocument
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixChangeDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse

private const val CODEFIX_GENERATION = "CODEFIX_GENERATION"
private const val CODEFIX_LOADING = "CODEFIX_LOADING"
private const val CODEFIX_PRESENTATION = "CODEFIX_PRESENTATION"
private const val CODEFIX_ERROR = "CODEFIX_ERROR"

private const val UNEXPECTED_ERROR = "An unexpected error happened during the generation, check the logs for more details"

class CodeFixTabPanel(
    private val project: Project,
    private val file: VirtualFile,
    private val issueId: UUID
) : JBPanel<CodeFixTabPanel>() {

    private lateinit var codefixPresentationPanel: JBPanel<CodeFixTabPanel>
    private lateinit var errorLabel: JBLabel
    private val presentationImage = JBLabel()
    private val cardLayout = CardLayout()

    init {
        layout = cardLayout

        add(initGenerationCard(), CODEFIX_GENERATION)
        add(initLoadingCard(), CODEFIX_LOADING)
        add(initGeneratedCard(), CODEFIX_PRESENTATION)
        add(initErrorCard(), CODEFIX_ERROR)

        val fixSuggestion = getService(project, FixSuggestionInlayHolder::class.java).getFixSuggestion(issueId.toString())
        if (fixSuggestion != null) {
            displaySuggestion(fixSuggestion, true)
        } else {
            switchCard(CODEFIX_GENERATION)
        }
    }

    private fun initGenerateButton(): JButton {
        return JButton("Generate Fix", SonarLintIcons.SPARKLE_GUTTER_ICON).apply {
            ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            alignmentX = Component.CENTER_ALIGNMENT
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            addActionListener {
                runOnPooledThread(project) { loadSuggestion() }
            }
        }
    }

    private fun initGenerationCard(): JScrollPane {
        val generateButton = initGenerateButton()

        val title = JBLabel("Fix your issues faster with AI CodeFix").apply {
            alignmentX = Component.LEFT_ALIGNMENT
            font = font.deriveFont(Font.BOLD, 16f)
        }

        val description = JBLabel("Sonar's AI CodeFix offers AI-generated code fixes for issues detected in your code").apply {
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val learnMore = HyperlinkLabel("Learn More")
        learnMore.addHyperlinkListener { BrowserUtil.browse(SonarLintDocumentation.Intellij.AI_CAPABILITIES) }

        val buttonPanel = JBPanel<CodeFixTabPanel>().apply {
            add(generateButton)
            add(Box.createHorizontalStrut(30))
            add(learnMore)
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(preferredSize.width, 40)
            maximumSize = Dimension(maximumSize.width, 40)
        }
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

        val centerPanel = JBPanel<CodeFixTabPanel>().apply {
            add(Box.createVerticalGlue())
            add(title)
            add(Box.createRigidArea(Dimension(0, 10)))
            add(description)
            add(Box.createRigidArea(Dimension(0, 10)))
            add(buttonPanel)
            add(Box.createVerticalGlue())
        }
        centerPanel.layout = BoxLayout(centerPanel, BoxLayout.Y_AXIS)

        val mainPanel = JBPanel<CodeFixTabPanel>(BorderLayout()).apply {
            add(presentationImage.apply {
                border = JBUI.Borders.empty(10)
                icon = SonarLintIcons.CODEFIX_PRESENTATION
            }, BorderLayout.WEST)
            add(centerPanel, BorderLayout.CENTER)
        }

        return initScrollPane(mainPanel)
    }

    private fun initLoadingCard(): JScrollPane {
        val description = JBLabel("A fix is being generated…").apply {
            font = JBFont.label().asBold()
            alignmentX = Component.CENTER_ALIGNMENT
        }

        val loadingIcon = JBLabel(SonarLintIcons.loadingCodeFixIcon()).apply {
            alignmentX = Component.CENTER_ALIGNMENT
        }

        val mainPanel = JBPanel<CodeFixTabPanel>().apply {
            add(Box.createVerticalGlue())
            add(loadingIcon)
            add(Box.createVerticalStrut(20))
            add(description)
            add(Box.createVerticalGlue())
        }
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        return initScrollPane(mainPanel)
    }

    private fun initGeneratedCard(): JScrollPane {
        codefixPresentationPanel = JBPanel<CodeFixTabPanel>(VerticalFlowLayout(0, 0))
        return initScrollPane(codefixPresentationPanel)
    }

    private fun initErrorCard(): JScrollPane {
        errorLabel = JBLabel().apply {
            alignmentX = Component.CENTER_ALIGNMENT
        }

        val generateButton = initGenerateButton()
        val panel = JBPanel<CodeFixTabPanel>().apply {
            add(Box.createVerticalGlue())
            add(errorLabel)
            add(Box.createVerticalStrut(10))
            add(generateButton)
            add(Box.createVerticalGlue())
        }
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        return initScrollPane(panel)
    }

    private fun switchCard(cardName: String) {
        runOnUiThread(project) { cardLayout.show(this, cardName) }
    }

    fun loadSuggestion() {
        switchCard(CODEFIX_LOADING)
        val module = findModuleForFile(file, project) ?: run {
            handleErrorMessage(null)
            switchCard(CODEFIX_ERROR)
            return
        }
        getService(BackendService::class.java).suggestAiCodeFixSuggestion(module, issueId)
            .thenAcceptAsync { fixSuggestion ->
                getService(project, FixSuggestionInlayHolder::class.java).addFixSuggestion(issueId.toString(), fixSuggestion)
                displaySuggestion(fixSuggestion, false)
            }
            .exceptionally { error ->
                handleErrorMessage(error)
                switchCard(CODEFIX_ERROR)
                null
            }
    }

    private fun handleErrorMessage(error: Throwable?) {
        if (error == null) {
            errorLabel.text = UNEXPECTED_ERROR
            return
        }
        when (val cause = error.cause) {
            is ResponseErrorException -> {
                when (cause.responseError.code) {
                    ResponseErrorCode.ParseError.value -> errorLabel.text = "The provided issue cannot be fixed"
                    SonarLintRpcErrorCode.ISSUE_NOT_FOUND -> errorLabel.text = "The provided issue does not exist, try triggering a new analysis"
                    SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND -> errorLabel.text = "The current project is not bound"
                    SonarLintRpcErrorCode.CONNECTION_NOT_FOUND -> errorLabel.text = "The current project is bound to an unknown connection"
                    SonarLintRpcErrorCode.CONNECTION_KIND_NOT_SUPPORTED -> errorLabel.text = "The current project is not bound to SonarQube Cloud"
                    SonarLintRpcErrorCode.FILE_NOT_FOUND -> errorLabel.text = "The file is considered unknown, reopen your project or modify the file"
                    SonarLintRpcErrorCode.TOO_MANY_REQUESTS -> errorLabel.text = "Fix generation is currently unavailable. " +
                        "Your organization has reached the monthly usage limit for AI CodeFix. " +
                        "To continue using this feature, please contact your SonarQube Server instance (or Cloud organization) administrator."
                    else -> errorLabel.text = UNEXPECTED_ERROR
                }
            }
            else -> {
                error.message?.let { SonarLintConsole.get(project).error(it, error) }
                errorLabel.text = UNEXPECTED_ERROR
            }
        }
    }

    private fun displaySuggestion(fixSuggestion: SuggestFixResponse, alreadySuggested: Boolean) {
        runOnUiThread(project, ModalityState.stateForComponent(this)) {
            ShowFixSuggestion(project, file).show(fixSuggestion, alreadySuggested)

            codefixPresentationPanel.removeAll()
            val explanationTitleLabel = JBLabel("Explanation").apply {
                font = JBFont.label().asBold()
            }
            val explanationLabel = SwingHelper.createHtmlViewer(false, null, null, null).apply {
                text = fixSuggestion.explanation
            }
            codefixPresentationPanel.add(explanationTitleLabel)
            codefixPresentationPanel.add(Box.createVerticalStrut(20))
            codefixPresentationPanel.add(explanationLabel)
            try {
                fixSuggestion.changes.forEachIndexed { index, change ->
                    codefixPresentationPanel.add(Box.createVerticalStrut(20))

                    val snippetLabel = JBLabel("AI CodeFix Snippet ${index + 1}").apply {
                        font = JBFont.label().asBold()
                    }
                    codefixPresentationPanel.add(snippetLabel, BorderLayout.NORTH)

                    codefixPresentationPanel.add(JBPanel<CodeFixTabPanel>(VerticalFlowLayout(20, 5)).apply {
                        add(RoundedPanelWithBackgroundColor(JBColor(Gray._236, Gray._72)).apply {
                            layout = VerticalFlowLayout(5, 5)
                            add(generateCodeFixSnippet(fixSuggestion, change))
                        })
                    })
                }
                switchCard(CODEFIX_PRESENTATION)
            } catch (e: IllegalStateException) {
                handleErrorMessage(e)
                switchCard(CODEFIX_ERROR)
            }
        }
    }

    private fun generateCodeFixSnippet(fixSuggestion: SuggestFixResponse, change: SuggestFixChangeDto): JBPanel<CodeFixTabPanel> {
        val panel = JBPanel<CodeFixTabPanel>(BorderLayout()).apply {
            isOpaque = false
        }

        file.getDocument()?.let {
            val rangeMarker = it.createRangeMarker(it.getLineStartOffset(change.startLine - 1), it.getLineEndOffset(change.endLine - 1))
            val currentCode = if (DocumentUtil.isValidOffset(rangeMarker.startOffset, it) && DocumentUtil.isValidOffset(rangeMarker.endOffset, it)) {
                it.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))
            } else {
                error("The fix was not applicable, the file could have been modified")
            }
            panel.add(CodeFixDiffView(project, file, currentCode, change.newCode), BorderLayout.CENTER)
        }

        val navigateButton = JButton("Navigate to line").apply {
            isOpaque = false
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        }

        navigateButton.addActionListener {
            val descriptor = OpenFileDescriptor(project, file, change.startLine - 1, -1)
            runOnUiThread(project, ModalityState.defaultModalityState()) {
                FileEditorManager.getInstance(project).openTextEditor(
                    descriptor, true
                )
            }
        }

        val reopenButton = JButton("Reopen all").apply {
            isOpaque = false
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        }

        reopenButton.addActionListener {
            ShowFixSuggestion(project, file).show(fixSuggestion, false)
        }

        val buttonPanel = JBPanel<CodeFixTabPanel>().apply {
            isOpaque = false
            add(navigateButton, BorderLayout.WEST)
            add(reopenButton, BorderLayout.WEST)
        }
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun initScrollPane(component: Component): JScrollPane {
        return ScrollPaneFactory.createScrollPane(component, true).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 10
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
        }
    }

}
