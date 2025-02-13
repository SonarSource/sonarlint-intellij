package org.sonarlint.intellij.ui.codefix

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
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
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fix.FixChanges
import org.sonarlint.intellij.fix.LocalFixSuggestion
import org.sonarlint.intellij.fix.ShowFixSuggestion
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarlint.intellij.util.getDocument

private const val CODEFIX_GENERATION = "CODEFIX_GENERATION"
private const val CODEFIX_LOADING = "CODEFIX_LOADING"
private const val CODEFIX_PRESENTATION = "CODEFIX_PRESENTATION"

class CodeFixTabPanel(
    private val project: Project,
    private val file: VirtualFile,
    private val issueId: String,
    private val disposableParent: Disposable
) : JBPanel<CodeFixTabPanel>() {

    private lateinit var loadingDecorator: ProgressBarLoadingDecorator
    private lateinit var codefixPresentationPanel: JBPanel<CodeFixTabPanel>
    private val cardLayout = CardLayout()

    init {
        layout = cardLayout

        add(initGenerationCard(), CODEFIX_GENERATION)
        add(initLoadingCard(), CODEFIX_LOADING)
        add(initGeneratedCard(), CODEFIX_PRESENTATION)

        val fixSuggestion = getService(project, FixSuggestionInlayHolder::class.java).getFixSuggestion(issueId)
        if (fixSuggestion != null) {
            displaySuggestion(fixSuggestion, false)
        } else {
            switchCard(CODEFIX_GENERATION)
        }
    }

    private fun initGenerationCard(): JScrollPane {
        val codeFixImg = JBLabel(SonarLintIcons.CODEFIX)
        codeFixImg.alignmentX = Component.CENTER_ALIGNMENT
        val generateButton = JButton("Generate Fix").apply {
            alignmentX = Component.CENTER_ALIGNMENT
            preferredSize = Dimension(200, preferredSize.height)
        }
        generateButton.addActionListener {
            loadSuggestion()
        }

        val panel = JBPanel<CodeFixTabPanel>().apply {
            add(Box.createVerticalGlue())
            add(codeFixImg)
            add(Box.createVerticalStrut(10))
            add(generateButton)
            add(Box.createVerticalGlue())
        }
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        return ScrollPaneFactory.createScrollPane(panel, true).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 10
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
        }
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

        return ScrollPaneFactory.createScrollPane(panel, true).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 10
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
        }
    }

    private fun initGeneratedCard(): JScrollPane {
        codefixPresentationPanel = JBPanel<CodeFixTabPanel>(VerticalFlowLayout(0, 0))

        return ScrollPaneFactory.createScrollPane(codefixPresentationPanel, true).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 10
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
        }
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
        getService(BackendService::class.java).suggestAiCodeFixSuggestion(project, issueId).thenAcceptAsync { fixSuggestion ->
            displaySuggestion(fixSuggestion, true)
        }
    }

    private fun displaySuggestion(fixSuggestion: LocalFixSuggestion, firstTime: Boolean) {
        runOnUiThread(project, ModalityState.stateForComponent(this)) {
            loadingDecorator.stopLoading()
            ShowFixSuggestion(project, file).show(fixSuggestion, firstTime)

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

    private fun generateCodeFixSnippet(change: FixChanges): JBPanel<CodeFixTabPanel> {
        val panel = JBPanel<CodeFixTabPanel>(BorderLayout()).apply {
            isOpaque = false
        }

        file.getDocument()?.let {
            panel.add(generateDiffView(it, change.startLine, change.endLine, change.newCode), BorderLayout.CENTER)
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

        val request = SimpleDiffRequest(
            "Diff Between Code Examples",
            DiffContentFactory.getInstance().create(project, currentCode),
            DiffContentFactory.getInstance().create(project, newCode),
            "Current code",
            "Suggested code"
        )

        val diffView = SonarQubeDiffView(project)
        diffView.applyRequest(request)
        Disposer.register(disposableParent, diffView)

        return diffView.component
    }
}
