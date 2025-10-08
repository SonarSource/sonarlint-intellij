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
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
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
import javax.swing.JEditorPane
import javax.swing.JProgressBar
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.ProjectUtils
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
    var codeSnippet: String? = null,
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
        val inputPanel = JBPanel<AIContextPanel>(BorderLayout())
        inputPanel.border = JBUI.Borders.empty(20, 20, 15, 20)

        // Main input row with label, field, and buttons
        val inputRowPanel = JBPanel<AIContextPanel>(FlowLayout(FlowLayout.LEFT, 8, 0))
        
        // Question label (styled)
        val styledLabel = JBLabel("Ask a question:").apply {
            font = UIUtil.getLabelFont()
            foreground = UIUtil.getLabelForeground()
        }
        
        questionField = JBTextField(50).apply {
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && isEnabled) {
                        handleAskQuestion()
                    }
                }
            })
            toolTipText = "Ask questions about your codebase (press Enter to submit)"
        }

        askButton = JButton("Ask").apply {
            addActionListener { handleAskQuestion() }
            preferredSize = Dimension(70, questionField.preferredSize.height)
            toolTipText = "Search the codebase for relevant information"
        }

        clearButton = JButton("Clear").apply {
            addActionListener { clearResults() }
            preferredSize = Dimension(60, questionField.preferredSize.height)
            isEnabled = false
            toolTipText = "Clear search results"
        }

        // Add components to input row
        inputRowPanel.add(styledLabel)
        inputRowPanel.add(questionField)
        inputRowPanel.add(askButton)
        inputRowPanel.add(clearButton)

        inputPanel.add(inputRowPanel, BorderLayout.CENTER)
        
        // Add separator line
        val separator = JBPanel<AIContextPanel>().apply {
            preferredSize = Dimension(0, 1)
            background = UIUtil.getPanelBackground().darker()
            isOpaque = true
        }
        inputPanel.add(separator, BorderLayout.SOUTH)

        return inputPanel
    }

    private fun createResultsPanel(): JBPanel<AIContextPanel> {
        resultsPanel = JBPanel<AIContextPanel>(BorderLayout())
        resultsPanel.border = JBUI.Borders.empty(10, 20, 20, 20)

        // Description panel at the top (styled)
        descriptionPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isVisible = false
            border = JBUI.Borders.compound(
                JBUI.Borders.emptyBottom(15),
                JBUI.Borders.customLine(UIUtil.getPanelBackground().darker().brighter(), 0, 0, 1, 0)
            )
        }

        // Container for results with vertical layout
        resultsContainer = JBPanel<AIContextPanel>()
        resultsContainer.layout = BoxLayout(resultsContainer, BoxLayout.Y_AXIS)
        
        // Main content panel with description at top and results below
        val mainContentPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(5)
        }
        mainContentPanel.add(descriptionPanel, BorderLayout.NORTH)
        mainContentPanel.add(resultsContainer, BorderLayout.CENTER)

        // Enhanced loading panel
        loadingPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isVisible = false
            border = JBUI.Borders.empty(60, 40)
            background = UIUtil.getPanelBackground()
        }
        
        progressBar = JProgressBar().apply {
            isIndeterminate = true
            isStringPainted = true
            string = "Analyzing codebase..."
            preferredSize = Dimension(200, 20)
        }
        
        val loadingLabel = JBLabel("🔍 Searching for relevant code...", SwingConstants.CENTER).apply {
            font = UIUtil.getLabelFont()
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyBottom(15)
        }
        
        val loadingContainer = JBPanel<AIContextPanel>()
        loadingContainer.layout = BoxLayout(loadingContainer, BoxLayout.Y_AXIS)
        loadingContainer.add(loadingLabel)
        loadingContainer.add(progressBar)
        
        loadingPanel.add(loadingContainer, BorderLayout.CENTER)

        // Enhanced message panel for no results or errors
        messagePanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isVisible = false
            border = JBUI.Borders.empty(40, 30)
        }

        resultsScrollPane = JBScrollPane(mainContentPanel).apply {
            preferredSize = Dimension(0, 400)
            minimumSize = Dimension(0, 250)
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.customLine(UIUtil.getPanelBackground().darker(), 1)
            background = UIUtil.getListBackground()
        }

        // Start with results panel visible
        resultsPanel.add(resultsScrollPane, BorderLayout.CENTER)
        return resultsPanel
    }

    private fun handleAskQuestion() {
        val question = questionField.text.trim()
        if (question.isEmpty()) {
            questionField.requestFocus()
            return
        }

        GlobalLogOutput.get().log("AI Context: Asking question: '$question'", ClientLogOutput.Level.INFO)

        // Clear previous results and show loading state
        clearResults()
        showLoadingState()
        
        // Update UI state
        askButton.isEnabled = false
        askButton.text = "Searching..."
        questionField.isEnabled = false
        clearButton.isEnabled = false
        
        // Call the AI Context service
        getService(BackendService::class.java)
            .askCodebaseQuestion(project, question)
            .thenAcceptAsync { response ->
                runOnUiThread(project) {
                    try {
                        GlobalLogOutput.get().log("AI Context: Received ${response.locations.size} location(s) from backend", ClientLogOutput.Level.INFO)
                        
                        hideLoadingState()
                        
                        // Show description if present
                        if (!response.text.isNullOrBlank()) {
                            showDescription(response.text!!)
                        } else {
                            hideDescription()
                        }
                        
                        // Convert backend response to UI model
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
                        
                        if (results.isEmpty() && response.text.isNullOrBlank()) {
                            showNoResultsMessage()
                        } else {
                            setResults(results)
                        }
                        
                        GlobalLogOutput.get().log("AI Context: Question processing completed successfully", ClientLogOutput.Level.INFO)
                    } catch (e: Exception) {
                        GlobalLogOutput.get().logError("AI Context: Error processing response", e)
                        showErrorMessage("An unexpected error occurred while processing the results.")
                    } finally {
                        // Always restore UI state
                        restoreUIState()
                    }
                }
            }
            .exceptionally { error ->
                runOnUiThread(project) {
                    GlobalLogOutput.get().logError("AI Context: Backend request failed for question '$question'", error)
                    
                    hideLoadingState()
                    val errorMessage = when {
                        error.message?.contains("timeout", true) == true -> 
                            "Request timed out. Please try again with a simpler question."
                        error.message?.contains("connection", true) == true -> 
                            "Connection error. Please check your network and try again."
                        error.message?.contains("backend", true) == true -> 
                            "Backend service unavailable. Please try again later."
                        else -> 
                            "Unable to process your question. Please try rephrasing it or try again later."
                    }
                    showErrorMessage(errorMessage)
                    restoreUIState()
                }
                null
            }
    }
    
    private fun restoreUIState() {
        askButton.isEnabled = true
        askButton.text = "Ask"
        questionField.isEnabled = true
        clearButton.isEnabled = results.isNotEmpty()
    }

    private fun clearResults() {
        GlobalLogOutput.get().log("AI Context: Clearing ${results.size} results", ClientLogOutput.Level.DEBUG)
        results.clear()
        resultsContainer.removeAll()
        hideDescription()
        hideLoadingState()
        hideMessagePanel()
        clearButton.isEnabled = false
        
        // Batch UI updates to reduce flickering
        SwingUtilities.invokeLater {
            resultsContainer.revalidate()
            resultsContainer.repaint()
        }
    }

    private fun createResultPanel(result: FileLocationResult): JBPanel<AIContextPanel> {
        val mainPanel = JBPanel<AIContextPanel>(BorderLayout())
        mainPanel.border = JBUI.Borders.compound(
            JBUI.Borders.emptyBottom(4),
            JBUI.Borders.customLineBottom(UIUtil.getPanelBackground().darker())
        )
        mainPanel.background = UIUtil.getListBackground()
        
        // Set fixed height for consistent spacing
        mainPanel.maximumSize = Dimension(Int.MAX_VALUE, 80)
        mainPanel.preferredSize = Dimension(0, 80)

        // Header panel with file info
        val headerPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            border = JBUI.Borders.empty(14, 16, 12, 16)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            background = UIUtil.getListBackground()
            isOpaque = true
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
        // Expand/collapse indicator next to file name
        val expandLabel = JBLabel(if (result.isExpanded) "▼" else "▶").apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyRight(8)
            preferredSize = Dimension(16, 16)
            horizontalAlignment = SwingConstants.CENTER
        }
        
        val fileLabel = JBLabel("<html><b>$fileName</b></html>").apply {
            font = UIUtil.getLabelFont()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            
            // Add click listener to open file directly
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 1) {
                        GlobalLogOutput.get().log("AI Context: Single-clicked on file name ${result.filePath}", ClientLogOutput.Level.INFO)
                        openFileAtLocation(result)
                    }
                }
                
                override fun mouseEntered(e: MouseEvent) {
                    foreground = JBColor.BLUE
                }
                
                override fun mouseExited(e: MouseEvent) {
                    foreground = UIUtil.getLabelForeground()
                }
            })
        }
        
        // File info container (filename, path, status) - aligned under filename text
        val fileInfoPanel = JBPanel<AIContextPanel>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        
        fileInfoPanel.add(fileLabel)
        
        val pathLabel = JBLabel("$pathPrefix ($lineInfo)").apply {
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyTop(2)
        }
        fileInfoPanel.add(pathLabel)
        
        val statusLabel = JBLabel("Click to expand • Click file name to open").apply {
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyTop(4)
        }
        fileInfoPanel.add(statusLabel)

        // Main content with icon on left, file info on right
        val contentPanel = JBPanel<AIContextPanel>(BorderLayout()).apply {
            isOpaque = false
        }
        contentPanel.add(expandLabel, BorderLayout.WEST)
        contentPanel.add(fileInfoPanel, BorderLayout.CENTER)

        headerPanel.add(contentPanel, BorderLayout.CENTER)

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
                    JBUI.Borders.customLine(UIUtil.getPanelBackground().darker(), 1),
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
                        statusLabel.text = if (!result.isExpanded) "Loading code..." else "Click to expand • Click file name to open"
                        
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
                                    JBUI.Borders.customLine(UIUtil.getPanelBackground().darker(), 1),
                                    JBUI.Borders.empty(8)
                                )
                            }
                            val codeScrollPane = JBScrollPane(codeArea).apply {
                                maximumSize = Dimension(Int.MAX_VALUE, 150)
                                preferredSize = Dimension(0, 150.coerceAtMost(codeArea.preferredSize.height + 20))
                            }
                            codePanel.add(codeScrollPane, BorderLayout.CENTER)
                        }
                        
                        // Update status text
                        statusLabel.text = if (result.isExpanded) "Click to collapse • Click file name to open" else "Click to expand • Click file name to open"
                        
                        // Use SwingUtilities.invokeLater to smooth UI updates and reduce flickering
                        SwingUtilities.invokeLater {
                            mainPanel.revalidate()
                            mainPanel.repaint()
                        }
                    }
                }
            }
            
            override fun mouseEntered(e: MouseEvent) {
                // Use standard IntelliJ hover background
                headerPanel.background = UIUtil.getListSelectionBackground(false)
                expandLabel.foreground = JBColor.BLUE
                headerPanel.repaint()
            }
            
            override fun mouseExited(e: MouseEvent) {
                headerPanel.background = UIUtil.getListBackground()
                expandLabel.foreground = UIUtil.getContextHelpForeground()
                headerPanel.repaint()
            }
        }

        headerPanel.addMouseListener(clickListener)
        return mainPanel
    }

    private fun loadCodeSnippet(result: FileLocationResult) {
        try {
            GlobalLogOutput.get().log("AI Context: Loading code snippet for ${result.filePath} (lines ${result.startLine}-${result.endLine})", ClientLogOutput.Level.DEBUG)

            val virtualFile = tryFindFileYolo(result.filePath)
            
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

            val virtualFile = tryFindFileYolo(result.filePath)
            
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
            
            clearButton.isEnabled = results.isNotEmpty()
            
            // Single revalidate/repaint at the end to minimize flickering
            SwingUtilities.invokeLater {
                resultsContainer.revalidate()
                resultsContainer.repaint()
            }
            
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
        SwingUtilities.invokeLater {
            resultsPanel.revalidate()
            resultsPanel.repaint()
        }
    }

    private fun hideLoadingState() {
        // Switch back to results panel
        resultsPanel.removeAll()
        resultsPanel.add(resultsScrollPane, BorderLayout.CENTER)
        SwingUtilities.invokeLater {
            resultsPanel.revalidate()
            resultsPanel.repaint()
        }
    }

    private fun hideMessagePanel() {
        // Switch back to results panel
        resultsPanel.removeAll()
        resultsPanel.add(resultsScrollPane, BorderLayout.CENTER)
        SwingUtilities.invokeLater {
            resultsPanel.revalidate()
            resultsPanel.repaint()
        }
    }

    private fun showNoResultsMessage() {
        messagePanel.removeAll()
        
        val iconLabel = JBLabel("🔍", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.PLAIN, 24f)
            border = JBUI.Borders.emptyBottom(10)
        }
        
        val messageLabel = JBLabel("No relevant code found", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = UIUtil.getContextHelpForeground()
        }
        
        val suggestionLabel = JBLabel("<html><div style='text-align: center; width: 300px;'>Try rephrasing your question or being more specific about the functionality you're looking for.</div></html>", SwingConstants.CENTER).apply {
            font = UIUtil.getLabelFont()
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyTop(8)
        }
        
        val tipsLabel = JBLabel("<html><div style='text-align: center; color: #666; font-style: italic; width: 350px;'>Try questions like: \"authentication logic\", \"database connections\", or \"error handling\"</div></html>", SwingConstants.CENTER).apply {
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            border = JBUI.Borders.emptyTop(15)
        }
        
        val messageContainer = JBPanel<AIContextPanel>()
        messageContainer.layout = BoxLayout(messageContainer, BoxLayout.Y_AXIS)
        messageContainer.add(iconLabel)
        messageContainer.add(messageLabel)
        messageContainer.add(suggestionLabel)
        messageContainer.add(tipsLabel)
        
        messagePanel.add(messageContainer, BorderLayout.CENTER)
        
        // Switch to message panel
        resultsPanel.removeAll()
        resultsPanel.add(messagePanel, BorderLayout.CENTER)
        SwingUtilities.invokeLater {
            resultsPanel.revalidate()
            resultsPanel.repaint()
        }
        
        clearButton.isEnabled = false
        GlobalLogOutput.get().log("AI Context: Showing no results message", ClientLogOutput.Level.DEBUG)
    }

    private fun showErrorMessage(errorText: String) {
        messagePanel.removeAll()
        
        val iconLabel = JBLabel("⚠️", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.PLAIN, 24f)
            border = JBUI.Borders.emptyBottom(10)
        }
        
        val errorLabel = JBLabel("Unable to search codebase", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = JBColor.RED
        }
        
        val errorMessageLabel = JBLabel("<html><div style='text-align: center; width: 400px;'>$errorText</div></html>", SwingConstants.CENTER).apply {
            font = UIUtil.getLabelFont()
            foreground = UIUtil.getContextHelpForeground()
            border = JBUI.Borders.emptyTop(8)
        }
        
        val retryLabel = JBLabel("<html><div style='text-align: center; color: #666; font-style: italic;'>Please try again or check your connection</div></html>", SwingConstants.CENTER).apply {
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            border = JBUI.Borders.emptyTop(12)
        }
        
        val errorContainer = JBPanel<AIContextPanel>()
        errorContainer.layout = BoxLayout(errorContainer, BoxLayout.Y_AXIS)
        errorContainer.add(iconLabel)
        errorContainer.add(errorLabel)
        errorContainer.add(errorMessageLabel)
        errorContainer.add(retryLabel)
        
        messagePanel.add(errorContainer, BorderLayout.CENTER)
        
        // Switch to message panel
        resultsPanel.removeAll()
        resultsPanel.add(messagePanel, BorderLayout.CENTER)
        SwingUtilities.invokeLater {
            resultsPanel.revalidate()
            resultsPanel.repaint()
        }
        
        clearButton.isEnabled = false
        GlobalLogOutput.get().log("AI Context: Showing error message: $errorText", ClientLogOutput.Level.DEBUG)
    }

    private fun showDescription(text: String) {
        descriptionPanel.removeAll()
        
        // Clean up potential encoding issues
        val cleanText = text.replace("���", "").replace("\uFFFD", "").trim()
        if (cleanText.isBlank()) return
        
        // Create styled HTML content for inline rendering
        val htmlContent = """
            <html>
            <head>
                <style>
                    body { 
                        font-family: ${UIUtil.getLabelFont().family}; 
                        font-size: 10px; 
                        color: ${colorToHex(UIUtil.getLabelForeground())}; 
                        margin: 0; 
                        padding: 0;
                        line-height: 1.4;
                        background-color: transparent;
                    }
                    a { 
                        color: ${colorToHex(JBColor.BLUE)}; 
                        text-decoration: underline;
                    }
                    a:hover { 
                        color: ${colorToHex(JBColor.BLUE.darker())}; 
                    }
                </style>
            </head>
            <body>${cleanText.replace("\n", "<br>")}</body>
            </html>
        """.trimIndent()
        
        // Use JEditorPane for proper inline HTML rendering with clickable links
        val editorPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            isOpaque = false
            background = UIUtil.getPanelBackground()
            border = JBUI.Borders.empty(10, 15, 15, 15)
            
            setText(htmlContent)
            
            // Add hyperlink listener for sonarlint-file:// links
            addHyperlinkListener(object : HyperlinkListener {
                override fun hyperlinkUpdate(e: HyperlinkEvent) {
                    if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                        val url = e.url?.toString() ?: e.description
                        GlobalLogOutput.get().log("AI Context: Clicked on inline link: $url", ClientLogOutput.Level.DEBUG)
                        handleSonarLintFileLink(url)
                    }
                }
            })
        }
        
        // Wrap in scroll pane in case content is long
        val scrollPane = JBScrollPane(editorPane).apply {
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            maximumSize = Dimension(Int.MAX_VALUE, 200)
        }
        
        descriptionPanel.add(scrollPane, BorderLayout.CENTER)
        descriptionPanel.isVisible = true
        
        GlobalLogOutput.get().log("AI Context: Showing inline description with HTML: ${cleanText.take(100)}...", ClientLogOutput.Level.DEBUG)
    }
    
    /**
     * Convert a Color to hex string for CSS
     */
    private fun colorToHex(color: java.awt.Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }

    private fun hideDescription() {
        descriptionPanel.isVisible = false
        descriptionPanel.removeAll()
        SwingUtilities.invokeLater {
            descriptionPanel.revalidate()
            descriptionPanel.repaint()
        }
    }

    /**
     * Handle clicks on sonarlint-file:// links in the description
     */
    private fun handleSonarLintFileLink(urlString: String?) {
        if (urlString.isNullOrBlank()) return
        
        try {
            GlobalLogOutput.get().log("AI Context: Handling sonarlint-file link: $urlString", ClientLogOutput.Level.DEBUG)
            
            if (!urlString.startsWith("sonarlint-file://")) {
                GlobalLogOutput.get().log("AI Context: Not a sonarlint-file link, ignoring", ClientLogOutput.Level.DEBUG)
                return
            }
            
            // Parse the sonarlint-file:// URL
            // Format: sonarlint-file:///path/to/file?startline=4&endline=17
            val urlParts = urlString.removePrefix("sonarlint-file://")
            val queryIndex = urlParts.indexOf('?')
            
            val filePath = if (queryIndex >= 0) {
                urlParts.take(queryIndex)
            } else {
                urlParts
            }
            
            var startLine: Int? = null
            var endLine: Int? = null
            
            // Parse query parameters if present
            if (queryIndex >= 0) {
                val queryString = urlParts.substring(queryIndex + 1)
                val params = queryString.split('&')
                
                for (param in params) {
                    val keyValue = param.split('=')
                    if (keyValue.size == 2) {
                        when (keyValue[0]) {
                            "startline" -> startLine = keyValue[1].toIntOrNull()
                            "endline" -> endLine = keyValue[1].toIntOrNull()
                        }
                    }
                }
            }
            
            GlobalLogOutput.get().log("AI Context: Parsed file link - path: $filePath, startLine: $startLine, endLine: $endLine", ClientLogOutput.Level.DEBUG)
            
            // Open the file at the specified location
            openFileFromLink(filePath, startLine)
            
        } catch (e: Exception) {
            GlobalLogOutput.get().logError("AI Context: Error handling sonarlint-file link", e)
        }
    }
    
    /**
     * Open a file from a parsed sonarlint-file:// link
     */
    private fun openFileFromLink(filePath: String, startLine: Int?) {
        try {
            GlobalLogOutput.get().log("AI Context: Opening file from link: $filePath at line $startLine", ClientLogOutput.Level.INFO)

            val virtualFile = tryFindFileYolo(filePath)
            
            if (virtualFile != null && virtualFile.exists()) {
                // Open the file and navigate to the start line if available
                val descriptor = if (startLine != null) {
                    // Convert to 0-based indexing
                    OpenFileDescriptor(project, virtualFile, startLine - 1, 0)
                } else {
                    // Open at the beginning of the file
                    OpenFileDescriptor(project, virtualFile, 0, 0)
                }
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                GlobalLogOutput.get().log("AI Context: Successfully opened file from link: $filePath", ClientLogOutput.Level.DEBUG)
            } else {
                GlobalLogOutput.get().logError("AI Context: File not found from link: $filePath", null)
            }
        } catch (e: Exception) {
            GlobalLogOutput.get().logError("AI Context: Error opening file from link: $filePath", e)
        }
    }

    private fun tryFindFileYolo(filePath: String): VirtualFile? {
        val path = Paths.get(filePath)
        var virtualFile = tryFindFile(project, path)
        if (virtualFile == null) {
            virtualFile = ModuleManager.getInstance(project).modules.firstNotNullOfOrNull { module ->
                val contentRoot = ModuleRootManager.getInstance(module).contentRoots.firstOrNull() ?: return@firstNotNullOfOrNull null
                if (contentRoot.isDirectory) {
                    val matchedFile = ProjectUtils.findByRelativePath(contentRoot, path)
                    if (matchedFile != null) {
                        return@firstNotNullOfOrNull matchedFile
                    }
                } else {
                    // On some version of Rider, all source files are returned as individual content roots, so simply check for equality
                    if (contentRoot.path.endsWith(ProjectUtils.getSystemIndependentPath(path))) {
                        return@firstNotNullOfOrNull contentRoot
                    }
                }
                return@firstNotNullOfOrNull null
            }
        }
        return virtualFile
    }

    override fun dispose() {
        // Cleanup is handled automatically
    }
}


