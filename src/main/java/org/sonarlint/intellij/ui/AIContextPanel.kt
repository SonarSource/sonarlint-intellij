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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.CollapsiblePanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.backend.aicontext.CodeLocation

/**
 * Data class representing a file location result from AI Context search
 */
data class FileLocationResult(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val displayText: String,
    var isExpanded: Boolean = false,
    var codeSnippet: String? = null
)

class AIContextPanel(private val project: Project) : SimpleToolWindowPanel(false, false), Disposable {

    private lateinit var questionField: JBTextField
    private lateinit var askButton: JButton
    private lateinit var clearButton: JButton
    private lateinit var resultsContainer: JBPanel<AIContextPanel>
    private lateinit var resultsScrollPane: JBScrollPane
    private lateinit var loadingPanel: JBPanel<AIContextPanel>
    private lateinit var messagePanel: JBPanel<AIContextPanel>
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
        val resultsPanel = JBPanel<AIContextPanel>(BorderLayout())
        resultsPanel.border = JBUI.Borders.empty(0, 15, 15, 15)

        // Container for results with vertical layout
        resultsContainer = JBPanel<AIContextPanel>()
        resultsContainer.layout = BoxLayout(resultsContainer, BoxLayout.Y_AXIS)

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

        resultsScrollPane = JBScrollPane(resultsContainer).apply {
            preferredSize = Dimension(0, 300)
            minimumSize = Dimension(0, 200)
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
        }

        // Stack all panels in the results area
        val stackedPanel = JBPanel<AIContextPanel>(BorderLayout())
        stackedPanel.add(resultsScrollPane, BorderLayout.CENTER)
        stackedPanel.add(loadingPanel, BorderLayout.CENTER)
        stackedPanel.add(messagePanel, BorderLayout.CENTER)

        resultsPanel.add(stackedPanel, BorderLayout.CENTER)
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
                    
                    // Convert backend response to our UI model
                    val results = response.locations.map { location ->
                        GlobalLogOutput.get().log("AI Context: Processing location: ${location.filePath} (lines ${location.startLine}-${location.endLine})", ClientLogOutput.Level.DEBUG)
                        FileLocationResult(
                            location.filePath,
                            location.startLine,
                            location.endLine,
                            location.description
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

    private fun addPlaceholderResults(question: String) {
        GlobalLogOutput.get().log("AI Context: Using placeholder results for question: '$question'", ClientLogOutput.Level.INFO)
        
        // This is a placeholder implementation showing sample results
        // In the real implementation, this will be replaced with actual AI service calls
        val sampleResults = listOf(
            FileLocationResult(
                "src/main/java/org/sonarlint/intellij/ui/AIContextPanel.kt",
                25,
                45,
                "Panel initialization and UI setup"
            ),
            FileLocationResult(
                "src/main/java/org/sonarlint/intellij/ui/SonarLintToolWindowFactory.java",
                50,
                70,
                "Tool window creation and tab management"
            ),
            FileLocationResult(
                "src/main/java/org/sonarlint/intellij/ui/ToolWindowConstants.kt",
                22,
                30,
                "Tab title constants definition"
            )
        )

        setResults(sampleResults)
    }

    private fun clearResults() {
        GlobalLogOutput.get().log("AI Context: Clearing ${results.size} results", ClientLogOutput.Level.DEBUG)
        results.clear()
        resultsContainer.removeAll()
        hideLoadingState()
        hideMessagePanel()
        resultsScrollPane.isVisible = true
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

        val fileLabel = JBLabel("<html><b>$fileName</b> <span style='color: #888888;'>($pathPrefix lines ${result.startLine}-${result.endLine})</span></html>")
        
        // Description
        val descriptionLabel = JBLabel(result.displayText).apply {
            font = font.deriveFont(Font.PLAIN)
            foreground = UIUtil.getContextHelpForeground()
        }

        val infoPanel = JBPanel<AIContextPanel>()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.add(fileLabel)
        infoPanel.add(descriptionLabel)

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
                preferredSize = Dimension(0, Math.min(150, codeArea.preferredSize.height + 20))
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
                                preferredSize = Dimension(0, Math.min(150, codeArea.preferredSize.height + 20))
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
            
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://${project.basePath}/${result.filePath}")
            
            if (virtualFile != null && virtualFile.exists()) {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                if (document != null) {
                    val totalLines = document.lineCount
                    val startLine = Math.max(0, result.startLine - 1) // Convert to 0-based
                    val endLine = Math.min(totalLines - 1, result.endLine - 1) // Convert to 0-based
                    
                    if (startLine <= endLine) {
                        val startOffset = document.getLineStartOffset(startLine)
                        val endOffset = document.getLineEndOffset(endLine)
                        result.codeSnippet = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
                        GlobalLogOutput.get().log("AI Context: Successfully loaded ${result.codeSnippet?.lines()?.size ?: 0} lines of code", ClientLogOutput.Level.DEBUG)
                    } else {
                        GlobalLogOutput.get().log("AI Context: Invalid line range for ${result.filePath}: $startLine-$endLine", ClientLogOutput.Level.DEBUG)
                    }
                } else {
                    GlobalLogOutput.get().log("AI Context: Could not get document for ${result.filePath}", ClientLogOutput.Level.DEBUG)
                }
            } else {
                GlobalLogOutput.get().log("AI Context: Virtual file not found for ${result.filePath}", ClientLogOutput.Level.DEBUG)
            }
        } catch (e: Exception) {
            GlobalLogOutput.get().logError("AI Context: Error loading code snippet for ${result.filePath}", e)
            result.codeSnippet = "// Error loading code snippet: ${e.message}"
        }
    }

    private fun openFileAtLocation(result: FileLocationResult) {
        try {
            GlobalLogOutput.get().log("AI Context: Opening file ${result.filePath} at line ${result.startLine}", ClientLogOutput.Level.INFO)
            
            // Find the file using VirtualFileManager
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://${project.basePath}/${result.filePath}")
            
            if (virtualFile != null && virtualFile.exists()) {
                // Open the file and navigate to the start line (convert to 0-based indexing)
                val descriptor = OpenFileDescriptor(project, virtualFile, result.startLine - 1, 0)
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
            resultsScrollPane.isVisible = true
            
            this.results.addAll(results)
            results.forEachIndexed { index, result ->
                GlobalLogOutput.get().log("AI Context: Creating UI panel for result ${index + 1}: ${result.filePath}", ClientLogOutput.Level.DEBUG)
                resultsContainer.add(createResultPanel(result))
            }
            
            resultsContainer.revalidate()
            resultsContainer.repaint()
            clearButton.isEnabled = results.isNotEmpty()
            
            GlobalLogOutput.get().log("AI Context: UI updated with ${results.size} result(s)", ClientLogOutput.Level.DEBUG)
        }
    }

    private fun showLoadingState() {
        resultsScrollPane.isVisible = false
        messagePanel.isVisible = false
        loadingPanel.isVisible = true
    }

    private fun hideLoadingState() {
        loadingPanel.isVisible = false
    }

    private fun hideMessagePanel() {
        messagePanel.isVisible = false
    }

    private fun showNoResultsMessage() {
        resultsScrollPane.isVisible = false
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
        messagePanel.isVisible = true
        
        clearButton.isEnabled = false
    }

    private fun showErrorMessage(errorText: String) {
        resultsScrollPane.isVisible = false
        messagePanel.removeAll()
        
        val errorLabel = JBLabel("Error", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            foreground = Color.RED
        }
        
        val errorMessageLabel = JBLabel("<html><div style='text-align: center;'>$errorText</div></html>", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = Color.RED
            border = JBUI.Borders.emptyTop(10)
        }
        
        val errorContainer = JBPanel<AIContextPanel>()
        errorContainer.layout = BoxLayout(errorContainer, BoxLayout.Y_AXIS)
        errorContainer.add(errorLabel)
        errorContainer.add(errorMessageLabel)
        
        messagePanel.add(errorContainer, BorderLayout.CENTER)
        messagePanel.isVisible = true
        
        clearButton.isEnabled = false
    }

    override fun dispose() {
        // Cleanup is handled automatically
    }
}
