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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.ConfigurationPanel
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.messages.PLUGIN_STATUS_CHANGE_TOPIC
import org.sonarlint.intellij.messages.PluginStatusChangeListener
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.ruledescription.RuleLanguages

private val COLOR_GREEN = JBColor(Color(34, 139, 34), Color(80, 200, 80))
private val COLOR_BLUE = JBColor(Color(30, 100, 200), Color(100, 160, 255))
private val COLOR_RED = JBColor(Color(180, 30, 30), Color(230, 80, 80))

/** Cell padding applied uniformly to every column. */
private val CELL_PADDING = JBUI.Borders.empty(0, 8)

private val PREMIUM_LANGUAGES = listOf(
    "Text", "Secrets", "PL/SQL", "Scala", "Swift"
)


class SupportedLanguagesPanel(
    private val project: Project,
    private val onSetupConnectedMode: () -> Unit,
) : ConfigurationPanel<SonarLintProjectSettings> {

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

        ApplicationManager.getApplication()?.messageBus?.connect()
            ?.subscribe(PLUGIN_STATUS_CHANGE_TOPIC, PluginStatusChangeListener {
                fetchAndRefreshStatuses()
            })
    }

    private fun buildBanner() {
        bannerPanel.background = JBColor(Color(49, 52, 56), Color(49, 52, 56))
        bannerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(69, 73, 78), Color(69, 73, 78))),
            JBUI.Borders.empty(10, 14)
        )
        bannerPanel.layout = GridBagLayout()
        val gbc = GridBagConstraints().apply {
            gridx = 0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
        }

        val titleLabel = JBLabel("Get more from your analysis").apply {
            foreground = UIUtil.getLabelForeground()
            font = font.deriveFont(java.awt.Font.BOLD)
        }
        gbc.gridy = 0
        gbc.insets = JBUI.insetsBottom(4)
        bannerPanel.add(titleLabel, gbc)

        val descRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        descRow.add(JBLabel("Connect to ").apply { foreground = UIUtil.getLabelForeground() })

        val serverCloudLink = JBLabel("<html><a href=''>SonarQube Server or Cloud</a></html>").apply {
            foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
            cursor = java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    BrowserUtil.browse(SonarLintDocumentation.Intellij.CONNECTED_MODE_LINK)
                }
            })
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
        bannerPanel.isVisible = !settings.isBound()
        fetchAndRefreshStatuses()
    }

    private fun fetchAndRefreshStatuses() {
        getService(BackendService::class.java).getPluginStatuses(project)
            .thenAccept { response ->
                val rows = PluginStatusMapper.mapToRows(response.pluginStatuses)
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed) {
                        tableModel = SupportedLanguagesTableModel(rows)
                        table.model = tableModel
                        applyColumnRenderers()
                    }
                }, ModalityState.any())
            }
    }

    // -------------------------------------------------------------------------
    // Cell renderers
    // -------------------------------------------------------------------------

    private class LanguageCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
        ): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            if (value is SupportedLanguageRow) {
                val fileType = RuleLanguages.findFileTypeByRuleLanguage(value.language)
                label.icon = if (fileType is UnknownFileType) PlaceholderIcon else fileType.icon
                label.text = value.displayName
            }
            label.border = CELL_PADDING
            return label
        }
    }

    private class StatusCellRenderer : TableCellRenderer {

        private val DOT_AREA_WIDTH = JBUI.scale(GreenDotIcon.SIZE + 4)

        // Dot placeholder: fixed-width panel that shows the green dot for ACTIVE
        private val dotPlaceholder = object : JPanel() {
            var showDot = false
            init {
                isOpaque = false
                preferredSize = java.awt.Dimension(DOT_AREA_WIDTH, 0)
            }
            override fun paintComponent(g: Graphics) {
                if (showDot) GreenDotIcon.paintIcon(
                    this, g,
                    (width - GreenDotIcon.SIZE) / 2,
                    (height - GreenDotIcon.SIZE) / 2
                )
            }
        }

        private val textLabel = JLabel().apply {
            horizontalAlignment = SwingConstants.LEFT
        }

        private val cell = JPanel(BorderLayout()).apply {
            isOpaque = true
            add(dotPlaceholder, BorderLayout.WEST)
            add(textLabel, BorderLayout.CENTER)
        }

        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val bg = if (isSelected) table.selectionBackground else table.background
            val fg = if (isSelected) table.selectionForeground else table.foreground

            cell.background = bg
            textLabel.background = bg
            cell.border = CELL_PADDING
            textLabel.toolTipText = null
            dotPlaceholder.showDot = false

            if (value is AnalyzerStatus) {
                textLabel.text = value.label
                textLabel.toolTipText = value.tooltip

                textLabel.foreground = if (isSelected) fg else when (value) {
                    AnalyzerStatus.ACTIVE -> COLOR_GREEN
                    AnalyzerStatus.SYNCED -> COLOR_BLUE
                    AnalyzerStatus.FAILED -> COLOR_RED
                    else -> fg
                }

                dotPlaceholder.showDot = value == AnalyzerStatus.ACTIVE
            }

            return cell
        }
    }

    private class SourceCellRenderer : DefaultTableCellRenderer() {
        private val pluginVersion: String = getService(SonarLintPlugin::class.java).version

        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            if (value is AnalyzerSource) {
                label.icon = when (value) {
                    AnalyzerSource.SONARQUBE_SERVER -> SonarLintIcons.ICON_SONARQUBE_SERVER_16
                    AnalyzerSource.SONARQUBE_CLOUD -> SonarLintIcons.ICON_SONARQUBE_CLOUD_16
                    AnalyzerSource.LOCAL -> PlaceholderIcon
                }
                label.text = when (value) {
                    AnalyzerSource.LOCAL -> "${value.label} $pluginVersion"
                    else -> value.label
                }
                if (!isSelected && value != AnalyzerSource.LOCAL) {
                    label.foreground = COLOR_BLUE
                }
            }
            label.horizontalAlignment = SwingConstants.LEFT
            label.border = CELL_PADDING
            return label
        }
    }

    internal object PlaceholderIcon : Icon {
        private const val SIZE = 16

        override fun getIconWidth() = SIZE
        override fun getIconHeight() = SIZE

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            // Empty placeholder to reserve space for alignment
        }
    }

    internal object GreenDotIcon : Icon {
        const val SIZE = 8

        override fun getIconWidth() = SIZE
        override fun getIconHeight() = SIZE

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = COLOR_GREEN
            g2.fillOval(x, y, SIZE, SIZE)
            g2.dispose()
        }
    }

}
