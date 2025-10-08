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
package org.sonarlint.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JProgressBar
import javax.swing.JTextArea
import javax.swing.SwingConstants
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.ProjectUtils.tryFindFile
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

/**
 * Data class representing a file location result from AI Context search
 */
data class FileLocationResult(
    val filePath: String,
    val startLine: Int?,
    val endLine: Int?,
    var isExpanded: Boolean = false,
    var codeSnippet: String? = null
)

class AIContextPanel(private val project: Project) : SimpleToolWindowPanel(false, false), Disposable {

    private lateinit var questionField: JBTextField
    private lateinit var askButton: JButton
    private lateinit var clearButton: JButton
    private lateinit var resultsContainer: JBPanel<AIContextPanel>
    private lateinit var resultsScrollPane: JBScrollPane
    private lateinit var resultsPanel: JBPanel<AIContextPanel>
    private lateinit var loadingPanel: JBPanel<AIContextPanel>
    private lateinit var messagePanel: JBPanel<AIContextPanel>
    private lateinit var descriptionPanel: JBPanel<AIContextPanel>
    private lateinit var progressBar: JProgressBar
    private val results = mutableListOf<FileLocationResult>()
    private val questionLabel = JBLabel("Ask a question about the codebase:")

    init {
        setupContent()
    }

    private fun setupContent() {
        val contentPanel = JBPanel<AIContextPanel>(BorderLayout())

        // Input section at the top
        val inputPanel = createInputPanel()
        
        // Results section in the center
        val resultsPanel = createResultsPanel()

        contentPanel.add(inputPanel, BorderLayout.NORTH)
        contentPanel.add(resultsPanel, BorderLayout.CENTER)

        setContent(contentPanel)
    }

    private fun createInputPanel(): JBPanel<AIContextPanel> {
        val inputPanel = JBPanel<AIContextPanel>(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false))
        inputPanel.border = JBUI.Borders.empty(15, 15, 10, 15)

        // Question label
        questionLabel.border = JBUI.Borders.emptyBottom(5)
        inputPanel.add(questionLabel)

        // Input field and buttons row
        val inputRowPanel = JBPanel<AIContextPanel>(FlowLayout(FlowLayout.LEFT, 0, 0))
        
        questionField = JBTextField(30).apply {
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        handleAskQuestion()
                    }
                }
            })
        }

        askButton = JButton("Ask").apply {
            addActionListener { handleAskQuestion() }
            preferredSize = Dimension(60, questionField.preferredSize.height)
        }

        clearButton = JButton("Clear").apply {
            addActionListener { clearResults() }
            preferredSize = Dimension(60, questionField.preferredSize.height)
            isEnabled = false // Initially disabled since no results
        }

        inputRowPanel.add(questionField)
        inputRowPanel.add(JBPanel<AIContextPanel>().apply { 
            preferredSize = Dimension(10, 0) 
        }) // Spacer
        inputRowPanel.add(askButton)
        inputRowPanel.add(JBPanel<AIContextPanel>().apply { 
            preferredSize = Dimension(5, 0) 
        }) // Spacer
        inputRowPanel.add(clearButton)

        inputPanel.add(inputRowPanel)

        return inputPanel
    }

    private fun createResultsPanel(): JBPanel<AIContextPanel> {
        resultsPanel = JBPanel<AIContextPanel>(BorderLayout())
        resultsPanel.border = JBUI.Borders.empty(0, 15, 15, 15)

        // Description panel at the top
        descriptionPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isVisible = false
            border = JBUI.Borders.empty(10, 15, 15, 15)
        }

        // Container for results with vertical layout
        resultsContainer = JBPanel<AIContextPanel>()
        resultsContainer.layout = BoxLayout(resultsContainer, BoxLayout.Y_AXIS)
        
        // Main content panel with description at top and results below
        val mainContentPanel = JBPanel<AIContextPanel>(BorderLayout())
        mainContentPanel.add(descriptionPanel, BorderLayout.NORTH)
        mainContentPanel.add(resultsContainer, BorderLayout.CENTER)

        // Loading panel with spinner
        loadingPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isVisible = false
            border = JBUI.Borders.empty(50, 20)
        }
        
        progressBar = JProgressBar().apply {
            isIndeterminate = true
            isStringPainted = true
            string = "Processing question..."
        }
        
        val loadingLabel = JBLabel("Searching codebase...", SwingConstants.CENTER)
        loadingPanel.add(loadingLabel, BorderLayout.NORTH)
        loadingPanel.add(progressBar, BorderLayout.CENTER)

        // Message panel for no results or errors
        messagePanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isVisible = false
            border = JBUI.Borders.empty(50, 20)
        }

        resultsScrollPane = JBScrollPane(mainContentPanel).apply {
            preferredSize = Dimension(0, 300)
            minimumSize = Dimension(0, 200)
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
        }

        // Start with results panel visible
        resultsPanel.add(resultsScrollPane, BorderLayout.CENTER)
        return resultsPanel
    }

    private fun handleAskQuestion() {
        val question = questionField.text.trim()
        if (question.isEmpty()) {
            GlobalLogOutput.get().log("AI Context: Empty question submitted, ignoring", ClientLogOutput.Level.DEBUG)
            return
        }

        GlobalLogOutput.get().log("AI Context: Asking question: '$question'", ClientLogOutput.Level.INFO)

        // Clear previous results automatically when asking a new question
        clearResults()
        
        // Show loading state
        showLoadingState()
        
        // Disable the ask button while processing
        askButton.isEnabled = false
        askButton.text = "Asking..."
        
        // Call the actual AI Context service via Backend
        getService(BackendService::class.java)
            .askCodebaseQuestion(project, question)
            .thenAcceptAsync { response ->
                runOnUiThread(project) {
                    GlobalLogOutput.get().log("AI Context: Received ${response.locations.size} location(s) from backend", ClientLogOutput.Level.INFO)
                    
                    // Hide loading state
                    hideLoadingState()
                    
                    // Show description if present
                    if (!response.text.isNullOrBlank()) {
                        showDescription(response.text!!)
                    } else {
                        hideDescription()
                    }
                    
                    // Convert backend response to our UI model
                    val results = response.locations.map { location ->
                        val startLine = location.textRange?.startLine
                        val endLine = location.textRange?.endLine
                        GlobalLogOutput.get().log("AI Context: Processing location: ${location.fileRelativePath} (lines $startLine-$endLine)", ClientLogOutput.Level.DEBUG)
                        FileLocationResult(
                            location.fileRelativePath,
                            startLine,
                            endLine
                        )
                    }
                    
                    if (results.isEmpty()) {
                        showNoResultsMessage()
                    } else {
                        setResults(results)
                    }
                    
                    // Re-enable the ask button
                    askButton.isEnabled = true
                    askButton.text = "Ask"
                    
                    GlobalLogOutput.get().log("AI Context: Question processing completed successfully", ClientLogOutput.Level.INFO)
                }
            }
            .exceptionally { error ->
                runOnUiThread(project) {
                    GlobalLogOutput.get().logError("AI Context: Error processing question '$question'", error)
                    
                    // Hide loading state and show error
                    hideLoadingState()
                    showErrorMessage("Failed to process question: ${error.message}")
                    
                    // Re-enable the ask button
                    askButton.isEnabled = true
                    askButton.text = "Ask"
                }
                null
            }
    }

    private fun clearResults() {
        GlobalLogOutput.get().log("AI Context: Clearing ${results.size} results", ClientLogOutput.Level.DEBUG)
        results.clear()
        resultsContainer.removeAll()
        hideDescription()
        hideLoadingState()
        hideMessagePanel()
        resultsContainer.revalidate()
        resultsContainer.repaint()
        clearButton.isEnabled = false
    }

    private fun createResultPanel(result: FileLocationResult): JBPanel<AIContextPanel> {
        val mainPanel = JBPanel<AIContextPanel>(BorderLayout())
        mainPanel.border = JBUI.Borders.compound(
            JBUI.Borders.emptyBottom(5),
            JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0)
        )
        
        // Set fixed height for consistent spacing
        mainPanel.maximumSize = Dimension(Int.MAX_VALUE, 80)
        mainPanel.preferredSize = Dimension(0, 80)

        // Header panel with file info
        val headerPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 12)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // File name (bold) and line info
        val fileName = result.filePath.substringAfterLast("/")
        val filePathDir = result.filePath.substringBeforeLast("/", "")
        val pathPrefix = if (filePathDir.isNotEmpty()) "$filePathDir/" else ""

        val lineInfo = if (result.startLine != null && result.endLine != null) {
            "lines ${result.startLine}-${result.endLine}"
        } else {
            "entire file"
        }
        val fileLabel = JBLabel("<html><b>$fileName</b> <span style='color: #888888;'>($pathPrefix $lineInfo)</span></html>")

        val infoPanel = JBPanel<AIContextPanel>()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.add(fileLabel)

        headerPanel.add(infoPanel, BorderLayout.CENTER)

        // Expand/collapse indicator
        val expandLabel = JBLabel(if (result.isExpanded) "▼" else "▶").apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        headerPanel.add(expandLabel, BorderLayout.EAST)

        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Code snippet panel (initially hidden)
        val codePanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isVisible = result.isExpanded
            border = JBUI.Borders.empty(0, 12, 8, 12)
            maximumSize = Dimension(Int.MAX_VALUE, 150) // Limit code snippet height
        }

        if (result.isExpanded && result.codeSnippet != null) {
            val codeArea = JTextArea(result.codeSnippet).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                background = UIUtil.getTextFieldBackground()
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1),
                    JBUI.Borders.empty(8)
                )
            }
            val codeScrollPane = JBScrollPane(codeArea).apply {
                maximumSize = Dimension(Int.MAX_VALUE, 150)
                preferredSize = Dimension(0, 150.coerceAtMost(codeArea.preferredSize.height + 20))
            }
            codePanel.add(codeScrollPane, BorderLayout.CENTER)
        }

        mainPanel.add(codePanel, BorderLayout.CENTER)

        // Click handling
        val clickListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when (e.clickCount) {
                    1 -> {
                        // Single click - toggle code snippet
                        result.isExpanded = !result.isExpanded
                        
                        if (result.isExpanded && result.codeSnippet == null) {
                            // Load code snippet if not already loaded
                            loadCodeSnippet(result)
                        }
                        
                        // Update UI
                        expandLabel.text = if (result.isExpanded) "▼" else "▶"
                        codePanel.isVisible = result.isExpanded
                        
                        // Adjust panel height based on expansion state
                        mainPanel.maximumSize = if (result.isExpanded) {
                            Dimension(Int.MAX_VALUE, 230) // Expanded height
                        } else {
                            Dimension(Int.MAX_VALUE, 80)  // Collapsed height
                        }
                        mainPanel.preferredSize = if (result.isExpanded) {
                            Dimension(0, 230)
                        } else {
                            Dimension(0, 80)
                        }
                        
                        if (result.isExpanded && result.codeSnippet != null) {
                            // Update code display
                            codePanel.removeAll()
                            val codeArea = JTextArea(result.codeSnippet).apply {
                                isEditable = false
                                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                                background = UIUtil.getTextFieldBackground()
                                border = JBUI.Borders.compound(
                                    JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1),
                                    JBUI.Borders.empty(8)
                                )
                            }
                            val codeScrollPane = JBScrollPane(codeArea).apply {
                                maximumSize = Dimension(Int.MAX_VALUE, 150)
                                preferredSize = Dimension(0, 150.coerceAtMost(codeArea.preferredSize.height + 20))
                            }
                            codePanel.add(codeScrollPane, BorderLayout.CENTER)
                        }
                        
                        mainPanel.revalidate()
                        mainPanel.repaint()
                    }
                    2 -> {
                        // Double click - open file
                        openFileAtLocation(result)
                    }
                }
            }
        }

        headerPanel.addMouseListener(clickListener)
        return mainPanel
    }

    private fun loadCodeSnippet(result: FileLocationResult) {
        try {
            GlobalLogOutput.get().log("AI Context: Loading code snippet for ${result.filePath} (lines ${result.startLine}-${result.endLine})", ClientLogOutput.Level.DEBUG)

            val path = Paths.get(result.filePath)
            val virtualFile = tryFindFile(project, path)
            
            if (virtualFile != null && virtualFile.exists()) {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                if (document != null) {
                    val totalLines = document.lineCount
                    
                    if (result.startLine != null && result.endLine != null) {
                        // Load specific line range
                        val startLine = 0.coerceAtLeast(result.startLine - 1) // Convert to 0-based
                        val endLine = (totalLines - 1).coerceAtMost(result.endLine - 1) // Convert to 0-based
                        
                        if (startLine <= endLine) {
                            val startOffset = document.getLineStartOffset(startLine)
                            val endOffset = document.getLineEndOffset(endLine)
                            result.codeSnippet = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
                            GlobalLogOutput.get().log("AI Context: Successfully loaded ${result.codeSnippet?.lines()?.size ?: 0} lines of code", ClientLogOutput.Level.DEBUG)
                        } else {
                            GlobalLogOutput.get().log("AI Context: Invalid line range for ${result.filePath}: $startLine-$endLine", ClientLogOutput.Level.DEBUG)
                            result.codeSnippet = "// Invalid line range"
                        }
                    } else {
                        // Load entire file (or first 50 lines to avoid huge files)
                        val linesToShow = 50.coerceAtMost(totalLines)
                        val endLine = linesToShow - 1
                        val startOffset = document.getLineStartOffset(0)
                        val endOffset = document.getLineEndOffset(endLine)
                        result.codeSnippet = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
                        if (totalLines > 50) {
                            result.codeSnippet += "\n\n// ... (showing first 50 lines of $totalLines total lines)"
                        }
                        GlobalLogOutput.get().log("AI Context: Successfully loaded first $linesToShow lines of entire file", ClientLogOutput.Level.DEBUG)
                    }
                } else {
                    GlobalLogOutput.get().log("AI Context: Could not get document for ${result.filePath}", ClientLogOutput.Level.DEBUG)
                    result.codeSnippet = "// Could not load document"
                }
            } else {
                GlobalLogOutput.get().log("AI Context: Virtual file not found for ${result.filePath}", ClientLogOutput.Level.DEBUG)
                result.codeSnippet = "// File not found: ${result.filePath}"
            }
        } catch (e: Exception) {
            GlobalLogOutput.get().logError("AI Context: Error loading code snippet for ${result.filePath}", e)
            result.codeSnippet = "// Error loading code snippet: ${e.message}"
        }
    }

    private fun openFileAtLocation(result: FileLocationResult) {
        try {
            GlobalLogOutput.get().log("AI Context: Opening file ${result.filePath} at line ${result.startLine}", ClientLogOutput.Level.INFO)

            val path = Paths.get(result.filePath)
            val virtualFile = tryFindFile(project, path)
            
            if (virtualFile != null && virtualFile.exists()) {
                // Open the file and navigate to the start line if available
                val descriptor = if (result.startLine != null) {
                    // Convert to 0-based indexing
                    OpenFileDescriptor(project, virtualFile, result.startLine - 1, 0)
                } else {
                    // Open at the beginning of the file
                    OpenFileDescriptor(project, virtualFile, 0, 0)
                }
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                GlobalLogOutput.get().log("AI Context: Successfully opened file ${result.filePath}", ClientLogOutput.Level.DEBUG)
            } else {
                GlobalLogOutput.get().logError("AI Context: File not found: ${result.filePath}", null)
            }
        } catch (e: Exception) {
            GlobalLogOutput.get().logError("AI Context: Error opening file ${result.filePath}", e)
        }
    }

    /**
     * Public method to add results from external AI service
     * This will be called by the AI service integration
     */
    fun setResults(results: List<FileLocationResult>) {
        GlobalLogOutput.get().log("AI Context: Setting ${results.size} result(s) in UI", ClientLogOutput.Level.INFO)
        
        runOnUiThread(project) {
            this.results.clear()
            resultsContainer.removeAll()
            hideMessagePanel()
            
            this.results.addAll(results)
            results.forEachIndexed { index, result ->
                GlobalLogOutput.get().log("AI Context: Creating UI panel for result ${index + 1}: ${result.filePath}", ClientLogOutput.Level.DEBUG)
                resultsContainer.add(createResultPanel(result))
            }
            
            resultsContainer.revalidate()
            resultsContainer.repaint()
            
            // Also refresh the parent container to ensure proper layout
            this@AIContextPanel.revalidate()
            this@AIContextPanel.repaint()
            
            clearButton.isEnabled = results.isNotEmpty()
            
            GlobalLogOutput.get().log("AI Context: UI updated with ${results.size} result(s)", ClientLogOutput.Level.DEBUG)
            GlobalLogOutput.get().log("AI Context: Results container visible: ${resultsScrollPane.isVisible}, has ${resultsContainer.componentCount} components", ClientLogOutput.Level.DEBUG)
            
            // Debug component sizes and visibility
            GlobalLogOutput.get().log("AI Context: ResultsScrollPane size: ${resultsScrollPane.size}, bounds: ${resultsScrollPane.bounds}", ClientLogOutput.Level.DEBUG)
            GlobalLogOutput.get().log("AI Context: ResultsContainer size: ${resultsContainer.size}, bounds: ${resultsContainer.bounds}", ClientLogOutput.Level.DEBUG)
            GlobalLogOutput.get().log("AI Context: ResultsContainer preferred size: ${resultsContainer.preferredSize}", ClientLogOutput.Level.DEBUG)
            
            // Debug individual component sizes
            for (i in 0 until resultsContainer.componentCount) {
                val component = resultsContainer.getComponent(i)
                GlobalLogOutput.get().log("AI Context: Component $i size: ${component.size}, bounds: ${component.bounds}, visible: ${component.isVisible}", ClientLogOutput.Level.DEBUG)
            }
        }
    }

    private fun showLoadingState() {
        resultsPanel.removeAll()
        resultsPanel.add(loadingPanel, BorderLayout.CENTER)
        resultsPanel.revalidate()
        resultsPanel.repaint()
    }

    private fun hideLoadingState() {
        // Switch back to results panel
        resultsPanel.removeAll()
        resultsPanel.add(resultsScrollPane, BorderLayout.CENTER)
        resultsPanel.revalidate()
        resultsPanel.repaint()
    }

    private fun hideMessagePanel() {
        // Switch back to results panel
        resultsPanel.removeAll()
        resultsPanel.add(resultsScrollPane, BorderLayout.CENTER)
        resultsPanel.revalidate()
        resultsPanel.repaint()
    }

    private fun showNoResultsMessage() {
        messagePanel.removeAll()
        
        val messageLabel = JBLabel("No results found for your question.", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.PLAIN, 14f)
            foreground = UIUtil.getContextHelpForeground()
        }
        
        val suggestionLabel = JBLabel("Try rephrasing your question or using different keywords.", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyTop(10)
        }
        
        val messageContainer = JBPanel<AIContextPanel>()
        messageContainer.layout = BoxLayout(messageContainer, BoxLayout.Y_AXIS)
        messageContainer.add(messageLabel)
        messageContainer.add(suggestionLabel)
        
        messagePanel.add(messageContainer, BorderLayout.CENTER)
        
        // Switch to message panel
        resultsPanel.removeAll()
        resultsPanel.add(messagePanel, BorderLayout.CENTER)
        resultsPanel.revalidate()
        resultsPanel.repaint()
        
        clearButton.isEnabled = false
    }

    private fun showErrorMessage(errorText: String) {
        messagePanel.removeAll()
        
        val errorLabel = JBLabel("Error", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            foreground = JBColor.RED
        }
        
        val errorMessageLabel = JBLabel("<html><div style='text-align: center;'>$errorText</div></html>", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = JBColor.RED
            border = JBUI.Borders.emptyTop(10)
        }
        
        val errorContainer = JBPanel<AIContextPanel>()
        errorContainer.layout = BoxLayout(errorContainer, BoxLayout.Y_AXIS)
        errorContainer.add(errorLabel)
        errorContainer.add(errorMessageLabel)
        
        messagePanel.add(errorContainer, BorderLayout.CENTER)
        
        // Switch to message panel
        resultsPanel.removeAll()
        resultsPanel.add(messagePanel, BorderLayout.CENTER)
        resultsPanel.revalidate()
        resultsPanel.repaint()
        
        clearButton.isEnabled = false
    }

    private fun showDescription(text: String) {
        descriptionPanel.removeAll()
        
        val descriptionLabel = JBLabel("<html><div style='width: 400px; font-family: sans-serif;'>$text</div></html>").apply {
            font = font.deriveFont(Font.PLAIN, 13f)
            foreground = UIUtil.getLabelForeground()
            border = JBUI.Borders.empty(5)
        }
        
        descriptionPanel.add(descriptionLabel, BorderLayout.CENTER)
        descriptionPanel.isVisible = true
        descriptionPanel.revalidate()
        descriptionPanel.repaint()
        
        GlobalLogOutput.get().log("AI Context: Showing description: ${text.take(100)}...", ClientLogOutput.Level.DEBUG)
    }

    private fun hideDescription() {
        descriptionPanel.isVisible = false
        descriptionPanel.removeAll()
        descriptionPanel.revalidate()
        descriptionPanel.repaint()
    }

    override fun dispose() {
        // Cleanup is handled automatically
    }
}
