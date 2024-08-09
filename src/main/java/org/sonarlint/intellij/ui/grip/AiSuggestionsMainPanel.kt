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

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel
import javax.swing.border.LineBorder
import org.sonarlint.intellij.actions.RestartBackendAction
import org.sonarlint.intellij.actions.RestartBackendAction.Companion.SONARLINT_ERROR_MSG
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.CardPanel
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter
import org.sonarlint.intellij.util.SonarLintActions

private const val SPLIT_PROPORTION_PROPERTY = "SONARLINT_AI_SUGGESTIONS_SPLIT_PROPORTION"
private const val DEFAULT_SPLIT_PROPORTION = 0.4f

private const val ERROR_CARD_ID = "ERROR_CARD"
private const val ISSUE_LIST_CARD_ID = "TREE_CARD"

private const val MAIN_SPLITTER_KEY = "sonarlint_ai_suggestions_splitter"

const val AI_TOOLBAR_GROUP_ID = "AiSuggestions"

class AiSuggestionsMainPanel(project: Project) : SimpleToolWindowPanel(false, true), DataProvider,
    Disposable {

    private val aiSuggestionsPanel = AiSuggestionsPanel(project, this)
    private val fixHistoryList: JBList<Finding>
    private val fixLocationsList: JBList<InlaySnippetData>
    private val cards = CardPanel()
    private val noAiSuggestions: JBPanelWithEmptyText
    private var selectedFinding: Finding? = null

    init {
        cards.add(
            centeredLabel(SONARLINT_ERROR_MSG, "Restart SonarLint Service", RestartBackendAction()), ERROR_CARD_ID
        )
        noAiSuggestions = centeredLabel("", "", null)
        aiSuggestionsPanel.minimumSize = Dimension(350, 200)

        val listModel = DefaultListModel<Finding>()
        fixHistoryList = JBList(listModel).apply {
            cellRenderer = AiSuggestionsItemRenderer()
        }
        fixHistoryList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixHistoryList.setEmptyText("Trigger an AI suggestion")
        fixHistoryList.border = BorderFactory.createCompoundBorder(
            LineBorder(JBColor.GRAY, 2, true),
            BorderFactory.createEmptyBorder()
        )
        val listScrollPanel = JBPanel<AiSuggestionsMainPanel>(BorderLayout()).apply {
            add(ScrollPaneFactory.createScrollPane(fixHistoryList))
            border = JBUI.Borders.empty(10)
        }
        val firstPanel = JBPanel<AiSuggestionsMainPanel>(BorderLayout())
        val firstLabel = JBLabel("History").apply {
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
            border = JBUI.Borders.emptyTop(10)
        }
        firstPanel.add(firstLabel, BorderLayout.NORTH)
        firstPanel.add(listScrollPanel, BorderLayout.CENTER)

        val secondListModel = DefaultListModel<InlaySnippetData>()
        fixLocationsList = JBList(secondListModel).apply {
            cellRenderer = AiLocationsItemRenderer()
        }
        fixLocationsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixLocationsList.addListSelectionListener {
            handleLocationClick(project, it.valueIsAdjusting, false)
        }
        fixLocationsList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                handleLocationClick(project, false, false)
            }
        })
        fixLocationsList.setEmptyText("Select a suggested fix to see the locations")
        fixLocationsList.apply {
            border = BorderFactory.createCompoundBorder(
                LineBorder(JBColor.GRAY, 2, true),
                BorderFactory.createEmptyBorder()
            )
        }

        val group = DefaultActionGroup()
        group.add(ReopenInlaySnippetAction())
        PopupHandler.installPopupMenu(fixLocationsList, group, ActionPlaces.TODO_VIEW_POPUP)
        EditSourceOnDoubleClickHandler.install(fixLocationsList)
        EditSourceOnEnterKeyHandler.install(fixLocationsList)

        val secondScrollPanel = JBPanel<AiSuggestionsMainPanel>(BorderLayout()).apply {
            add(ScrollPaneFactory.createScrollPane(fixLocationsList))
            border = JBUI.Borders.empty(10)
        }

        val secondPanel = JBPanel<AiSuggestionsMainPanel>(BorderLayout())
        val secondLabel = JBLabel("Locations").apply {
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        }
        val secondMinorLabel = JBLabel(" (click to navigate to the location or right click to re open the snippet)").apply {
            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
        }

        val labelPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(secondLabel)
            add(secondMinorLabel)
        }

        secondPanel.add(labelPanel, BorderLayout.NORTH)
        secondPanel.add(secondScrollPanel, BorderLayout.CENTER)

        val splitPane = JBSplitter(true, MAIN_SPLITTER_KEY, 0.5f)
        splitPane.firstComponent = firstPanel
        splitPane.secondComponent = secondPanel

        cards.add(
            createSplitter(project, this, this, splitPane, aiSuggestionsPanel, SPLIT_PROPORTION_PROPERTY, DEFAULT_SPLIT_PROPORTION),
            ISSUE_LIST_CARD_ID
        )

        fixHistoryList.addListSelectionListener {
            handleHistoryClick(project, it.valueIsAdjusting)
        }
        fixHistoryList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                handleHistoryClick(project, false)
            }
        })

        val issuesPanel = JBPanel<AiSuggestionsMainPanel>(BorderLayout())
        issuesPanel.add(cards.container, BorderLayout.CENTER)
        setContent(issuesPanel)

        val sonarLintActions = SonarLintActions.getInstance()
        setupToolbar(
            listOf(
                sonarLintActions.configure(),
                sonarLintActions.clearAiSuggestionAction()
            )
        )
        switchCard()
    }

    fun addFindings(findings: List<Finding>) {
        selectedFinding = findings.first()
        aiSuggestionsPanel.setSelectedFinding(findings.first())
        val listModel = fixHistoryList.model as DefaultListModel
        removeFinding(findings.first().getId())
        findings.forEach { listModel.add(0, it) }
        fixHistoryList.selectedIndex = 0
        switchCard()
    }

    fun addFinding(finding: Finding) {
        selectedFinding = finding
        aiSuggestionsPanel.setSelectedFinding(finding)
        val listModel = fixHistoryList.model as DefaultListModel
        removeFinding(finding.getId())
        listModel.add(0, finding)
        fixHistoryList.selectedIndex = 0
        switchCard()
    }

    fun addAiFailure(failureMessage: String, finding: Finding) {
        val listModel = fixHistoryList.model as DefaultListModel
        val f = listModel.elements().asSequence().firstOrNull { it.getId() == finding.getId() }
        if (f != null) {
            aiSuggestionsPanel.showFailureMessage(failureMessage, f)
            val index = listModel.indexOf(f)
            if (index != -1) {
                fixHistoryList.selectedIndex = index
            }
        }
        switchCard()
    }

    fun addAiResponse(aiResponse: String, finding: Finding) {
        val listModel = fixHistoryList.model as DefaultListModel
        val f = listModel.elements().asSequence().firstOrNull { it.getId() == finding.getId() }
        if (f != null) {
            aiSuggestionsPanel.replaceText(aiResponse, f)
            val index = listModel.indexOf(f)
            if (index != -1) {
                fixHistoryList.selectedIndex = index
                finding.module()?.project?.let { handleHistoryClick(it, false) }
            }
        }
        switchCard()
    }

    fun refresh(findingId: UUID) {
        selectedFinding?.let {
            if (findingId == it.getId()) {
                aiSuggestionsPanel.setSelectedFinding(it)
            }
        }
        switchCard()
    }

    fun removeFinding(findingId: UUID) {
        val listModel = fixHistoryList.model as DefaultListModel
        for (i in 0 until listModel.size()) {
            val finding = listModel.getElementAt(i)
            if (finding.getId() == findingId) {
                listModel.removeElementAt(i)
                break
            }
        }
        if (selectedFinding?.getId() == findingId) {
            selectedFinding = null
            aiSuggestionsPanel.clear()
        }
        switchCard()
    }

    private fun switchCard() {
        when {
            !getService(BackendService::class.java).isAlive() -> {
                showCard(ERROR_CARD_ID)
            }

            else -> {
                showCard(ISSUE_LIST_CARD_ID)
            }
        }
    }

    private fun showCard(id: String) {
        cards.show(id)
    }

    private fun setupToolbar(actions: List<AnAction>) {
        val group = DefaultActionGroup()
        actions.forEach { group.add(it) }
        val toolbar = ActionManager.getInstance().createActionToolbar(AI_TOOLBAR_GROUP_ID, group, false)
        toolbar.targetComponent = this
        val toolBarBox = Box.createHorizontalBox()
        toolBarBox.add(toolbar.component)
        setToolbar(toolBarBox)
        toolbar.component.isVisible = true
    }

    private fun centeredLabel(textLabel: String, actionText: String?, action: AnAction?): JBPanelWithEmptyText {
        val labelPanel = JBPanelWithEmptyText(HorizontalLayout(5))
        val text = labelPanel.emptyText
        text.setText(textLabel)
        if (action != null && actionText != null) {
            text.appendLine(
                actionText, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
            ) { _: ActionEvent? ->
                ActionUtil.invokeAction(
                    action, labelPanel, AI_TOOLBAR_GROUP_ID, null, null
                )
            }
        }
        return labelPanel
    }

    fun clear() {
        val historyModel = fixHistoryList.model as DefaultListModel
        historyModel.clear()
        val locationsModel = fixLocationsList.model as DefaultListModel
        locationsModel.clear()
        aiSuggestionsPanel.clear()
        switchCard()
    }

    fun handleLocationClick(project: Project, isAdjusting: Boolean, forceReopen: Boolean) {
        if (isAdjusting) return
        val selectedInlay = fixLocationsList.selectedValue
        if (selectedInlay != null) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val file = selectedInlay.inlayPanel.file
            val allOpenFiles = fileEditorManager.openFiles

            if (!allOpenFiles.contains(file)) {
                fileEditorManager.openFile(file, true)
            }

            val editor = fileEditorManager.openTextEditor(OpenFileDescriptor(project, file), true)
            if (editor != null) {
                try {
                    val document = editor.document
                    val lineStartOffset = document.getLineStartOffset(selectedInlay.inlayPanel.inlayLine - 1)
                    editor.caretModel.moveToOffset(lineStartOffset)
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)

                    if ((selectedInlay.inlayPanel.disposed && selectedInlay.status == AiFindingState.LOADED)
                        || (forceReopen && selectedInlay.status != AiFindingState.INIT && selectedInlay.status != AiFindingState.LOADING
                            && selectedInlay.status != AiFindingState.FAILED)
                    ) {
                        val inlayHolder = getService(project, InlayHolder::class.java)
                        selectedFinding?.let {
                            inlayHolder.regenerateInlay(selectedInlay, editor, selectedFinding!!.getMessage())
                            inlayHolder.removeInlayCodeSnippet(it.getId(), selectedInlay)
                            handleHistoryClick(project, false)
                        }
                    }
                } catch (e: IndexOutOfBoundsException) {
                    get(project).simpleNotification(
                        null,
                        "Line does not exist anymore. Please refresh the AI suggestion.",
                        NotificationType.WARNING
                    )
                }
            }
        }
    }

    private fun handleHistoryClick(project: Project, isAdjusting: Boolean) {
        if (isAdjusting) return
        selectedFinding = fixHistoryList.selectedValue
        val listModelLoc = fixLocationsList.model as DefaultListModel
        listModelLoc.clear()
        if (selectedFinding != null) {
            aiSuggestionsPanel.setSelectedFinding(selectedFinding!!)
            val inlayHolder = getService(project, InlayHolder::class.java)
            val inlays = inlayHolder.getInlayData(selectedFinding!!.getId())

            inlays?.inlaySnippets?.sortedBy { it.inlayPanel.inlayLine }?.forEach { inlay ->
                listModelLoc.addElement(inlay)
            }
        } else {
            aiSuggestionsPanel.clear()
        }
    }

    override fun dispose() {
        // nothing to do
    }

}
