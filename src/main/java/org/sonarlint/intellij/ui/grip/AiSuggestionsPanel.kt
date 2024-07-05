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
package org.sonarlint.intellij.ui.grip;

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.ProgressBarLoadingDecorator
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.SwingHelper
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JSeparator
import javax.swing.ScrollPaneConstants
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jdesktop.swingx.HorizontalLayout
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

enum class AiFindingState {
    INIT, LOADING, LOADED, FAILED, ACCEPTED, DECLINED, PARTIAL
}

class AiSuggestionsPanel(private val project: Project, private val disposableParent: Disposable) : SimpleToolWindowPanel(false, true),
    Disposable {
    private val mainPanel = JBPanelWithEmptyText(VerticalFlowLayout(5, 5))
    private val buttonPanel = JBPanel<AiSuggestionsPanel>(VerticalFlowLayout(5))
    private val feedback = JButton("Give Feedback!").apply { isEnabled = false }
    private val regenerate = JButton("Regenerate")
    private val wasRegenerated = AtomicBoolean(false)
    private var currentFinding: Finding? = null
    private val loadingPanel = JBPanel<JBPanel<*>>()
    private val loadingDecorator = ProgressBarLoadingDecorator(loadingPanel, disposableParent, 150)
    private val resultPerFinding = mutableMapOf<UUID, String>()

    init {
        layoutComponents()
    }

    fun setSelectedFinding(finding: Finding) {
        this.currentFinding = finding
        val foundResponse = resultPerFinding[finding.getId()]
        val inlayHolder = getService(project, InlayHolder::class.java)

        when (inlayHolder.getInlayData(finding.getId())?.status) {
            null, AiFindingState.INIT -> {
                mainPanel.removeAll()
                val statusText: StatusText = mainPanel.emptyText
                statusText.clear()
                statusText.appendLine(
                    "Suggest AI fix", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
                ) { _: ActionEvent? ->
                    inlayHolder.removeAndDisposeInlayCodeSnippets(finding.getId())
                    currentFinding?.let { cr -> setSelectedFinding(cr) }
                    ActionUtil.invokeAction(
                        SuggestAiFixAction(finding), this, AI_TOOLBAR_GROUP_ID, null, null
                    )
                }
            }

            AiFindingState.LOADING -> {
                showLoading()
            }

            AiFindingState.LOADED, AiFindingState.ACCEPTED, AiFindingState.DECLINED, AiFindingState.PARTIAL -> {
                replaceText(foundResponse!!, finding)
            }

            AiFindingState.FAILED -> {
                showFailureMessage(foundResponse!!, finding)
            }
        }
        revalidate()
        repaint()
    }

    private fun showLoading() {
        buttonPanel.isVisible = false
        mainPanel.emptyText.clear()
        mainPanel.removeAll()
        mainPanel.layout = GridBagLayout()

        loadingDecorator.component.preferredSize = Dimension(200, loadingDecorator.component.preferredSize.height)
        val loadingPanel = JBPanel<JBPanel<*>>(VerticalFlowLayout(5)).apply {
            add(JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                add(JBLabel("Loading AI suggestions..."))
            })
            add(loadingDecorator.component)
        }
        mainPanel.add(loadingPanel)
        loadingDecorator.startLoading(false)
    }

    fun showFailureMessage(failureMessage: String, finding: Finding) {
        buttonPanel.isVisible = false
        resultPerFinding[finding.getId()] = failureMessage
        loadingDecorator.stopLoading()
        mainPanel.removeAll()
        mainPanel.layout = VerticalFlowLayout(5, 5)
        val statusText: StatusText = mainPanel.emptyText
        statusText.text = failureMessage
        if (currentFinding != null) {
            statusText.appendLine(
                "Try again to suggest AI fix", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
            ) { _: ActionEvent? ->
                ActionUtil.invokeAction(
                    SuggestAiFixAction(currentFinding!!), this, AI_TOOLBAR_GROUP_ID, null, null
                )
            }
        }
    }

    fun replaceText(textResponse: String, finding: Finding) {
        wasRegenerated.set(false)
        resultPerFinding[finding.getId()] = textResponse
        val inlayHolder = getService(project, InlayHolder::class.java)

        regenerate.addActionListener {
            if (!wasRegenerated.get()) {
                inlayHolder.removeAndDisposeInlayCodeSnippets(finding.getId())
                currentFinding?.let { cr -> setSelectedFinding(cr) }
                ActionUtil.invokeAction(
                    SuggestAiFixAction(finding), this, AI_TOOLBAR_GROUP_ID, null, null
                )
                buttonPanel.isVisible = false
            }
            wasRegenerated.set(true)
        }

        feedback.action = object : AbstractAction("Submit Feedback") {
            override fun isEnabled(): Boolean {
                val inlayData = inlayHolder.getInlayData(finding.getId())
                return if (inlayData != null) {
                    inlayData.status == AiFindingState.ACCEPTED || inlayData.status == AiFindingState.DECLINED || inlayData.status == AiFindingState.PARTIAL
                } else {
                    false
                }
            }

            override fun actionPerformed(e: ActionEvent?) {
                currentFinding?.let { curr ->
                    runOnUiThread(project, ModalityState.stateForComponent(mainPanel)) {
                        GiveFeedbackDialog(project, curr.getId(), curr.getMessage()).show()
                    }
                }
            }
        }

        mainPanel.isVisible = false
        mainPanel.removeAll()
        loadingDecorator.stopLoading()
        mainPanel.layout = VerticalFlowLayout(5, 5)
        val snippets: List<Pair<String, Boolean>> = getSnippets(textResponse)
        for ((snippet, isCode) in snippets) {
            if (isCode) {
                mainPanel.add(CodeSnippet(project, FileTypeRegistry.getInstance().findFileTypeByName("JAVA"), snippet).apply {
                    Disposer.tryRegister(disposableParent, this)
                })
            } else {
                val flavour = CommonMarkFlavourDescriptor()
                val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(snippet.trim())
                val html = HtmlGenerator(snippet.trim(), parsedTree, flavour).generateHtml()
                mainPanel.add(SwingHelper.createHtmlViewer(false, null, null, null).apply {
                    text = html
                })
            }
        }

        mainPanel.isVisible = true
        buttonPanel.isVisible = true
    }

    private fun getSnippets(text: String): List<Pair<String, Boolean>> {
        val splitText = text.split("```").map { it.trim() }
        val snippets = mutableListOf<Pair<String, Boolean>>()

        for (i in splitText.indices) {
            val isCode = i % 2 != 0 // Code snippets are at odd indices after splitting
            snippets.add(Pair(splitText[i], isCode))
        }

        return snippets
    }

    private fun layoutComponents() {
        val statusText: StatusText = mainPanel.emptyText
        statusText.text = "Select a suggested fix to see the result"

        setLayout(BorderLayout())

        val buttonLinePanel = JBPanel<AiSuggestionsPanel>(HorizontalLayout(5)).apply {
            add(feedback)
            add(regenerate)
        }
        buttonPanel.isVisible = false

        val scrollPane = JBScrollPane(mainPanel)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        add(scrollPane, BorderLayout.CENTER)
        add(buttonPanel.apply {
            add(JSeparator())
            add(buttonLinePanel)
        }, BorderLayout.SOUTH)
    }

    fun clear() {
        this.resultPerFinding.remove(currentFinding?.getId())
        this.currentFinding = null
        val statusText: StatusText = mainPanel.emptyText
        statusText.text = "Select a suggested fix to see the result"
        this.mainPanel.removeAll()
        buttonPanel.isVisible = false
    }

    override fun dispose() {
        // Nothing to dispose
    }

}
