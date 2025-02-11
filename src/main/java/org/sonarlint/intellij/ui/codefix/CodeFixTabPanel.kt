package org.sonarlint.intellij.ui.codefix

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import org.jdesktop.swingx.HorizontalLayout
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.fix.FixChanges
import org.sonarlint.intellij.fix.LocalFixSuggestion
import org.sonarlint.intellij.fix.ShowFixSuggestion
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarlint.intellij.util.getDocument

class CodeFixTabPanel(private val project: Project, private val file: VirtualFile) : JBPanel<CodeFixTabPanel>(BorderLayout()) {

    private val generateButton: JButton

    init {
        val generationPanel = JBPanel<CodeFixTabPanel>(VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 15, true, false))
        val codeFixImg = JBLabel(SonarLintIcons.CODEFIX)
        codeFixImg.alignmentX = Component.CENTER_ALIGNMENT
        generateButton = JButton("Generate Fix")
        generateButton.alignmentX = Component.CENTER_ALIGNMENT
        generateButton.addActionListener {
            displaySuggestion()
        }
        generationPanel.add(Box.createVerticalGlue())
        generationPanel.add(codeFixImg)
        generationPanel.add(Box.createVerticalStrut(10))
        generationPanel.add(generateButton)
        generationPanel.add(Box.createVerticalGlue())
        add(generationPanel, BorderLayout.CENTER)
    }

    fun displaySuggestion() {
        removeAll()
        val suggestionPanel = JBPanel<CodeFixTabPanel>(VerticalFlowLayout())
        val changes = listOf(
            FixChanges(1, 2, null, """
                    new line
                    and new code here
                """.trimIndent()),
            FixChanges(5, 5, null, "second fix"),
        )
        val fixSuggestion = LocalFixSuggestion("suggestionid", "explanation", changes)
        val fixSuggestionInlayHolder = ShowFixSuggestion(project, file).show(fixSuggestion)

        val explanationLabel = JBLabel("Explanation: ${fixSuggestion.explanation}", SwingConstants.LEFT)
        explanationLabel.alignmentX = Component.LEFT_ALIGNMENT
        suggestionPanel.add(explanationLabel)
        changes.forEachIndexed { index, change ->
            suggestionPanel.add(Box.createVerticalStrut(10))
            val snippetLabel = JBLabel("AI CodeFix Snippet ${index + 1}", SwingConstants.LEFT)
            snippetLabel.alignmentX = Component.LEFT_ALIGNMENT
            suggestionPanel.add(snippetLabel)
            displayDiff(suggestionPanel, change.startLine, change.endLine, change.newCode)

            val applyButton = JButton("Apply").apply {
                foreground = JBColor.green
                font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
                addActionListener {
                    fixSuggestionInlayHolder[index].acceptFix()
                }
            }
            val declineButton = JButton("Decline").apply {
                foreground = JBColor.red
                font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
                addActionListener {
                    fixSuggestionInlayHolder[index].declineFix()
                }
            }
            val buttonPanel = JBPanel<CodeFixTabPanel>(BorderLayout()).apply {
                add(RoundedPanelWithBackgroundColor().apply {
                    layout = HorizontalLayout(5)
                    add(applyButton)
                    add(declineButton)
                }, BorderLayout.WEST)
            }
            suggestionPanel.add(buttonPanel)
        }
        val scrollPane = ScrollPaneFactory.createScrollPane(suggestionPanel, true).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 10
            isOpaque = false
            viewport.isOpaque = false
            border = JBUI.Borders.empty()
        }
        add(scrollPane)
    }

    private fun displayDiff(suggestionPanel: JBPanel<CodeFixTabPanel>, startLine: Int, endLine: Int, newCode: String) {
        file.getDocument()?.let {
            val rangeMarker = it.createRangeMarker(it.getLineStartOffset(startLine - 1), it.getLineEndOffset(endLine - 1))
            val currentCode = it.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))

            val request = SimpleDiffRequest(
                "Diff Between Code Examples",
                DiffContentFactory.getInstance().create(project, currentCode),
                DiffContentFactory.getInstance().create(project, newCode),
                "Current code",
                "Suggested code"
            )

            val diffView = SonarQubeDiffView(project)
            diffView.applyRequest(request)

            val diffPanel = JPanel(BorderLayout())
            diffPanel.border = JBUI.Borders.empty(10)
            diffPanel.add(diffView.component, BorderLayout.CENTER)

            suggestionPanel.add(diffView.component)
        }
    }
}
