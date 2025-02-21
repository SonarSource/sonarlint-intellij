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

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.ProgressBarLoadingDecorator
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fix.ShowFixSuggestion
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayHolder
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.getDocument
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixChangeDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse

private const val CODEFIX_GENERATION = "CODEFIX_GENERATION"
private const val CODEFIX_LOADING = "CODEFIX_LOADING"
private const val CODEFIX_PRESENTATION = "CODEFIX_PRESENTATION"
private const val CODEFIX_ERROR = "CODEFIX_ERROR"

class CodeFixTabPanel(
    private val project: Project,
    private val file: VirtualFile,
    private val issueId: String,
    private val disposableParent: Disposable
) : JBPanel<CodeFixTabPanel>() {

    private lateinit var loadingDecorator: ProgressBarLoadingDecorator
    private lateinit var codefixPresentationPanel: JBPanel<CodeFixTabPanel>
    private lateinit var errorLabel: JBLabel
    private val cardLayout = CardLayout()

    init {
        layout = cardLayout

        add(initGenerationCard(), CODEFIX_GENERATION)
        add(initLoadingCard(), CODEFIX_LOADING)
        add(initGeneratedCard(), CODEFIX_PRESENTATION)
        add(initErrorCard(), CODEFIX_ERROR)

        val fixSuggestion = getService(project, FixSuggestionInlayHolder::class.java).getFixSuggestion(issueId)
        if (fixSuggestion != null) {
            displaySuggestion(fixSuggestion, true)
        } else {
            switchCard(CODEFIX_GENERATION)
        }
    }

    private fun initGenerateButton(): JButton {
        return JButton("Generate Fix").apply {
            ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
            alignmentX = Component.CENTER_ALIGNMENT
            preferredSize = Dimension(200, preferredSize.height)
            addActionListener {
                runOnPooledThread(project) { loadSuggestion() }
            }
        }
    }

    private fun initGenerationCard(): JScrollPane {
        val codeFixImg = JBLabel(SonarLintIcons.CODEFIX)
        codeFixImg.alignmentX = Component.CENTER_ALIGNMENT
        val generateButton = initGenerateButton()

        val panel = JBPanel<CodeFixTabPanel>().apply {
            add(Box.createVerticalGlue())
            add(codeFixImg)
            add(Box.createVerticalStrut(10))
            add(generateButton)
            add(Box.createVerticalGlue())
        }
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        return initScrollPane(panel)
    }

    private fun initLoadingCard(): JScrollPane {
        val codeFixImg = JBLabel(SonarLintIcons.CODEFIX)
        codeFixImg.alignmentX = Component.CENTER_ALIGNMENT

        loadingDecorator = ProgressBarLoadingDecorator(JBPanel<CodeFixTabPanel>(), disposableParent, 150).apply {
            preferredSize = Dimension(200, preferredSize.height)
        }

        val panel = JBPanel<CodeFixTabPanel>().apply {
            add(Box.createVerticalGlue())
            add(codeFixImg)
            add(Box.createVerticalStrut(10))
            add(loadingDecorator.component)
            add(Box.createVerticalGlue())
        }
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        return initScrollPane(panel)
    }

    private fun initGeneratedCard(): JScrollPane {
        codefixPresentationPanel = JBPanel<CodeFixTabPanel>(VerticalFlowLayout(0, 0))
        return initScrollPane(codefixPresentationPanel)
    }

    private fun initErrorCard(): JScrollPane {
        errorLabel = JBLabel()

        val generateButton = initGenerateButton()
        val panel = JBPanel<CodeFixTabPanel>(BorderLayout()).apply {
            add(Box.createVerticalGlue())
            add(errorLabel)
            add(Box.createVerticalStrut(10))
            add(generateButton)
            add(Box.createVerticalGlue())
        }
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        return initScrollPane(panel)
    }

    private fun displayLoading() {
        switchCard(CODEFIX_LOADING)
        loadingDecorator.startLoading(false)
    }

    private fun switchCard(cardName: String) {
        runOnUiThread(project) { cardLayout.show(this, cardName) }
    }

    fun loadSuggestion() {
        displayLoading()
        val module = findModuleForFile(file, project) ?: run {
            runOnUiThread(project, ModalityState.stateForComponent(this)) {
                loadingDecorator.stopLoading()
            }
            return
        }
        getService(BackendService::class.java).suggestAiCodeFixSuggestion(module, issueId)
            .thenAcceptAsync { fixSuggestion ->
                getService(project, FixSuggestionInlayHolder::class.java).addFixSuggestion(issueId, fixSuggestion)
                displaySuggestion(fixSuggestion, false)
            }
            .exceptionally { error ->
                handleErrorMessage(error)
                switchCard(CODEFIX_ERROR)
                null
            }
    }

    private fun handleErrorMessage(error: Throwable) {
        error.message?.let {
            SonarLintConsole.get(project).error(it, error)
        }
        errorLabel.text = "An unexpected error happened during the generation"
    }

    private fun displaySuggestion(fixSuggestion: SuggestFixResponse, alreadySuggested: Boolean) {
        runOnUiThread(project, ModalityState.stateForComponent(this)) {
            loadingDecorator.stopLoading()
            ShowFixSuggestion(project, file).show(fixSuggestion, alreadySuggested)

            codefixPresentationPanel.removeAll()
            val explanationTitleLabel = JBLabel("Explanation").apply {
                font = JBFont.label().asBold()
            }
            val explanationLabel = JBLabel(fixSuggestion.explanation)
            codefixPresentationPanel.add(explanationTitleLabel)
            codefixPresentationPanel.add(Box.createVerticalStrut(20))
            codefixPresentationPanel.add(explanationLabel)
            fixSuggestion.changes.forEachIndexed { index, change ->
                codefixPresentationPanel.add(Box.createVerticalStrut(20))

                val snippetLabel = JBLabel("AI CodeFix Snippet ${index + 1}").apply {
                    font = JBFont.label().asBold()
                }
                codefixPresentationPanel.add(snippetLabel, BorderLayout.NORTH)

                codefixPresentationPanel.add(JBPanel<CodeFixTabPanel>(VerticalFlowLayout(20, 5)).apply {
                    add(RoundedPanelWithBackgroundColor(JBColor(Gray._236, Gray._72)).apply {
                        layout = VerticalFlowLayout(5, 5)
                        add(generateCodeFixSnippet(change))
                    })
                })
            }
            switchCard(CODEFIX_PRESENTATION)
        }
    }

    private fun generateCodeFixSnippet(change: SuggestFixChangeDto): JBPanel<CodeFixTabPanel> {
        val panel = JBPanel<CodeFixTabPanel>(BorderLayout()).apply {
            isOpaque = false
        }

        file.getDocument()?.let {
            panel.add(generateDiffView(it, change.startLine, change.endLine, change.newCode), BorderLayout.CENTER)
        }

        val navigateButton = JButton("Navigate to line").apply {
            isOpaque = false
            ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
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

        val buttonPanel = JBPanel<CodeFixTabPanel>(BorderLayout()).apply {
            isOpaque = false
            add(navigateButton, BorderLayout.WEST)
        }
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun generateDiffView(document: Document, startLine: Int, endLine: Int, newCode: String): JComponent {
        val rangeMarker = document.createRangeMarker(document.getLineStartOffset(startLine - 1), document.getLineEndOffset(endLine - 1))
        val currentCode = document.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))

        val diffPanel = DiffManager.getInstance().createRequestPanel(
            project,
            disposableParent,
            null
        )

        diffPanel.setRequest(
            SimpleDiffRequest(
                null,
                DiffContentFactory.getInstance().create(currentCode),
                DiffContentFactory.getInstance().create(newCode),
                null,
                null
            ).apply {
                putUserData(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, true)
            }
        )

        return diffPanel.component
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
