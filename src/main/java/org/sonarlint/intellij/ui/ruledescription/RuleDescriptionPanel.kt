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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.codefix.CodeFixTabPanel
import org.sonarlint.intellij.ui.ruledescription.RuleParsingUtils.Companion.parseCodeExamples
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleContextualSectionDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDescriptionTabDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleMonolithicDescriptionDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleSplitDescriptionDto

class RuleDescriptionPanel(private val project: Project, private val parent: Disposable) : JBPanel<RuleDescriptionPanel>(BorderLayout()) {

    private var sectionsTabs: JBTabbedPane? = null
    private var codeFixTab: CodeFixTabPanel? = null

    companion object {
        private const val AI_CODEFIX_TITLE = "AI CodeFix"
    }

    fun openCodeFixTabAndGenerate() {
        sectionsTabs.let {
            it!!.selectedIndex = it.indexOfTab(AI_CODEFIX_TITLE)
        }
        runOnPooledThread(project) { codeFixTab?.loadSuggestion() }
    }

    fun addMonolithWithCodeFix(monolithDescription: RuleMonolithicDescriptionDto, fileType: FileType, issueId: UUID, file: VirtualFile) {
        val sectionsTabs = JBTabbedPane()
        sectionsTabs.font = UIUtil.getLabelFont().deriveFont(Font.BOLD)

        sectionsTabs.insertTab("Description", null, createNonContextualTab(monolithDescription.htmlContent, fileType), null, 0)
        codeFixTab = CodeFixTabPanel(project, file, issueId)
        sectionsTabs.insertTab(AI_CODEFIX_TITLE, SonarLintIcons.SPARKLE_GUTTER_ICON, codeFixTab, null, 1)

        add(sectionsTabs, BorderLayout.CENTER)
        this.sectionsTabs = sectionsTabs
    }

    fun addMonolith(monolithDescription: RuleMonolithicDescriptionDto, fileType: FileType) {
        val scrollPane = parseCodeExamples(project, parent, monolithDescription.htmlContent, fileType)
        add(scrollPane, BorderLayout.CENTER)
    }

    fun addSectionsWithCodeFix(withSections: RuleSplitDescriptionDto, fileType: FileType, issueId: UUID, file: VirtualFile) {
        addSections(withSections, fileType)
        sectionsTabs?.let {
            codeFixTab = CodeFixTabPanel(project, file, issueId)
            it.insertTab(AI_CODEFIX_TITLE, SonarLintIcons.SPARKLE_GUTTER_ICON, codeFixTab, null, withSections.tabs.size)
        }
    }

    fun addSections(withSections: RuleSplitDescriptionDto, fileType: FileType) {
        val htmlHeader = withSections.introductionHtmlContent
        if (!htmlHeader.isNullOrBlank()) {
            val htmlViewer = RuleHtmlViewer(false)
            add(htmlViewer, BorderLayout.NORTH)
            htmlViewer.updateHtml(htmlHeader)
        }

        val sectionsTabs = JBTabbedPane()
        sectionsTabs.font = UIUtil.getLabelFont().deriveFont(Font.BOLD)

        withSections.tabs.forEachIndexed { index, tabDesc ->
            sectionsTabs.insertTab(tabDesc.title, null, createTab(tabDesc, fileType), null, index)
        }

        add(sectionsTabs, BorderLayout.CENTER)
        this.sectionsTabs = sectionsTabs
    }

    private fun createTab(tabDesc: RuleDescriptionTabDto, fileType: FileType): JBPanel<JBPanel<*>> {
        return tabDesc.content.map({ nonContextual ->
            createNonContextualTab(nonContextual.htmlContent, fileType)
        }, { contextual ->
            createContextualTab(contextual.defaultContextKey, contextual.contextualSections, fileType)
        })
    }

    private fun createNonContextualTab(htmlContent: String, fileType: FileType) : JBPanel<JBPanel<*>> {
        val sectionPanel = JBPanel<JBPanel<*>>(BorderLayout())
        val scrollPane = parseCodeExamples(project, parent, htmlContent, fileType)
        sectionPanel.add(scrollPane, BorderLayout.CENTER)
        return sectionPanel
    }

    private fun createContextualTab(
        defaultContextKey: String,
        contextualSections: List<RuleContextualSectionDto>,
        fileType: FileType
    ): JBPanel<JBPanel<*>> {
        val sectionPanel = JBPanel<JBPanel<*>>(BorderLayout())
        val comboPanel = JBPanel<JBPanel<*>>(HorizontalLayout(JBUI.scale(UIUtil.DEFAULT_HGAP)))
        comboPanel.add(JBLabel("Which component or framework contains the issue?"))
        val contextCombo = ComboBox(DefaultComboBoxModel(contextualSections.toTypedArray()))
        contextCombo.renderer = SimpleListCellRenderer.create("", RuleContextualSectionDto::getDisplayName)
        contextCombo.addActionListener {
            val layout = sectionPanel.layout as BorderLayout
            layout.getLayoutComponent(BorderLayout.CENTER)?.let { sectionPanel.remove(it) }

            val htmlContent = (contextCombo.selectedItem as RuleContextualSectionDto).htmlContent
            val scrollPane = parseCodeExamples(project, parent, htmlContent, fileType)
            sectionPanel.add(scrollPane, BorderLayout.CENTER)
        }
        comboPanel.add(contextCombo)
        sectionPanel.add(comboPanel, BorderLayout.NORTH)
        contextCombo.selectedIndex =
            contextualSections.indexOfFirst { sec -> sec.contextKey == defaultContextKey }
        return sectionPanel
    }

}
