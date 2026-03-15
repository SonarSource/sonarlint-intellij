/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.config.project

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.ConfigurationPanel
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.messages.PluginStatusChangeListener
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

private val BACKGROUND_COLOR = JBColor(Color(49, 52, 56), Color(49, 52, 56))
private val BORDER_COLOR = JBColor(Color(69, 73, 78), Color(69, 73, 78))

private val PREMIUM_LANGUAGES = listOf(
    "Text", "Secrets", "PL/SQL", "Scala", "Swift"
)

class SupportedLanguagesPanel(
    private val project: Project,
    private val onSetupConnectedMode: () -> Unit,
) : ConfigurationPanel<SonarLintProjectSettings>, PluginStatusChangeListener {

    private val panel = JPanel(BorderLayout())
    private val bannerPanel = JPanel(BorderLayout())
    private val table = JBTable()
    private var tableModel = SupportedLanguagesTableModel(emptyList())

    init {
        buildBanner()
        buildTable()

        val scrollPane = JBScrollPane(table)
        // Wrap banner + gap in a dedicated panel so the table gets proper breathing room
        val northPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(bannerPanel, BorderLayout.CENTER)
            add(Box.createVerticalStrut(JBUI.scale(8)), BorderLayout.SOUTH)
        }
        panel.add(northPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.border = JBUI.Borders.empty(8)
    }

    private fun buildBanner() {
        bannerPanel.background = BACKGROUND_COLOR
        bannerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            JBUI.Borders.empty(10, 14)
        )
        bannerPanel.layout = GridBagLayout()
        val gbc = GridBagConstraints().apply {
            gridx = 0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
        }

        val titleLabel = JBLabel("Get more from your analysis").apply {
            foreground = UIUtil.getLabelForeground()
            font = font.deriveFont(Font.BOLD)
        }
        gbc.gridy = 0
        gbc.insets = JBUI.insetsBottom(4)
        bannerPanel.add(titleLabel, gbc)

        val descRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        descRow.add(JBLabel("Connect to ").apply { foreground = UIUtil.getLabelForeground() })

        val serverCloudLink = HyperlinkLabel("SonarQube Server or Cloud").apply {
            addHyperlinkListener { BrowserUtil.browse(SonarLintDocumentation.Intellij.CONNECTED_MODE_LINK) }
        }
        descRow.add(serverCloudLink)

        descRow.add(JBLabel(" to unlock ").apply { foreground = UIUtil.getLabelForeground() })

        val premiumTooltipText = PREMIUM_LANGUAGES.joinToString(", ")
        val extendedLangLabel = JBLabel("<html><u>extended language support</u></html>").apply {
            foreground = UIUtil.getLabelForeground()
            toolTipText = premiumTooltipText
        }
        descRow.add(extendedLangLabel)

        descRow.add(JBLabel(" and advanced security rules for your existing code.").apply {
            foreground = UIUtil.getLabelForeground()
        })

        gbc.gridy = 1
        gbc.insets = JBUI.insetsBottom(8)
        bannerPanel.add(descRow, gbc)

        val setupButton = JButton("Set up connection").apply {
            addActionListener { onSetupConnectedMode() }
        }
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = JBUI.emptyInsets()
        bannerPanel.add(setupButton, gbc)

        bannerPanel.isVisible = false
    }

    private fun buildTable() {
        table.model = tableModel
        table.rowHeight = 26
        table.setShowGrid(false)
        table.intercellSpacing = JBUI.emptySize()
        table.tableHeader.reorderingAllowed = false

        applyColumnRenderers()
    }

    private fun applyColumnRenderers() {
        val cm = table.columnModel

        cm.getColumn(SupportedLanguagesTableModel.Column.ANALYSIS_TYPE.ordinal).apply {
            cellRenderer = LanguageCellRenderer()
            preferredWidth = 200
        }

        cm.getColumn(SupportedLanguagesTableModel.Column.STATUS.ordinal).apply {
            cellRenderer = StatusCellRenderer()
            preferredWidth = 150
        }

        cm.getColumn(SupportedLanguagesTableModel.Column.SOURCE.ordinal).apply {
            cellRenderer = SourceCellRenderer()
            preferredWidth = 200
        }
    }

    override fun getComponent(): JComponent = panel

    override fun isModified(settings: SonarLintProjectSettings): Boolean = false

    override fun save(settings: SonarLintProjectSettings) {
        // read-only panel
    }

    override fun load(settings: SonarLintProjectSettings) {
        bannerPanel.isVisible = !settings.isBound
        fetchAndRefreshStatuses()
    }

    private fun fetchAndRefreshStatuses() {
        getService(BackendService::class.java).getPluginStatuses(project)
            .thenAccept { response ->
                val rows = PluginStatusMapper.mapToRows(response.pluginStatuses)
                runOnUiThread(project, ModalityState.stateForComponent(panel)) {
                    if (!project.isDisposed) {
                        tableModel = SupportedLanguagesTableModel(rows)
                        table.model = tableModel
                        applyColumnRenderers()
                    }
                }
            }
            .exceptionally { error ->
                SonarLintConsole.get(project).error("Failed to fetch plugin statuses", error)
                null
            }
    }

    override fun pluginStatusesChanged() {
        fetchAndRefreshStatuses()
    }

}
