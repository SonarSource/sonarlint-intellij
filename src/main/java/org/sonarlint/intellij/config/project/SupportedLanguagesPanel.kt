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

import com.intellij.openapi.fileTypes.UnknownFileType
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
import java.awt.RenderingHints
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
import org.sonarlint.intellij.config.ConfigurationPanel
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.ruledescription.RuleLanguages

private val COLOR_GREEN = JBColor(Color(34, 139, 34), Color(80, 200, 80))
private val COLOR_BLUE = JBColor(Color(30, 100, 200), Color(100, 160, 255))
private val COLOR_RED = JBColor(Color(180, 30, 30), Color(230, 80, 80))
private val COLOR_DIMMED = UIUtil.getContextHelpForeground()

/** Cell padding applied uniformly to every column. */
private val CELL_PADDING = JBUI.Borders.empty(0, 8)

/** Whether this row's content should be visually dimmed (greyed out). */
private val AnalyzerStatus.isDimmed: Boolean
    get() = this == AnalyzerStatus.PREMIUM || this == AnalyzerStatus.UNSUPPORTED

class SupportedLanguagesPanel(private val onSetupConnectedMode: () -> Unit) : ConfigurationPanel<SonarLintProjectSettings> {

    private val panel = JPanel(BorderLayout())
    private val bannerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
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
        bannerPanel.background = JBColor(Color(255, 248, 225), Color(80, 70, 30))
        bannerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(230, 200, 100), Color(140, 120, 50))),
            JBUI.Borders.empty(6, 10)
        )

        val infoLabel = JBLabel("Connect to SonarQube Cloud or Server to access premium analyzers.  ")
        infoLabel.foreground = UIUtil.getLabelForeground()

        val setupLink = JButton("<html><a href=''>Setup Connected Mode</a></html>")
        setupLink.isBorderPainted = false
        setupLink.isContentAreaFilled = false
        setupLink.isFocusPainted = false
        setupLink.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
        setupLink.cursor = java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
        setupLink.addActionListener { onSetupConnectedMode() }

        bannerPanel.add(infoLabel)
        bannerPanel.add(setupLink)
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

        cm.getColumn(SupportedLanguagesTableModel.Column.LANGUAGE.ordinal).apply {
            cellRenderer = LanguageCellRenderer()
            preferredWidth = 200
        }

        cm.getColumn(SupportedLanguagesTableModel.Column.STATUS.ordinal).apply {
            cellRenderer = StatusCellRenderer()
            preferredWidth = 150
        }

        cm.getColumn(SupportedLanguagesTableModel.Column.SOURCE.ordinal).apply {
            cellRenderer = SourceCellRenderer()
            preferredWidth = 80
        }

        cm.getColumn(SupportedLanguagesTableModel.Column.VERSION.ordinal).apply {
            cellRenderer = VersionCellRenderer()
            preferredWidth = 130
        }
    }

    fun triggerSetupConnectedMode() = onSetupConnectedMode()

    override fun getComponent(): JComponent = panel

    override fun isModified(settings: SonarLintProjectSettings): Boolean = false

    override fun save(settings: SonarLintProjectSettings) {
        // read-only panel
    }

    override fun load(settings: SonarLintProjectSettings) {
        val isBound = settings.isBound()
        bannerPanel.isVisible = !isBound
        tableModel = SupportedLanguagesTableModel(buildMockedRows(isBound))
        table.model = tableModel
        applyColumnRenderers()
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
                label.icon = if (fileType is UnknownFileType) null else fileType.icon
                label.text = value.displayName
                if (!isSelected && value.status.isDimmed) {
                    label.foreground = COLOR_DIMMED
                }
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

        private val retryButton = JButton("Retry").apply {
            putClientProperty("ActionButton.smallVariant", true)
            isFocusPainted = false
            isOpaque = false
            border = JBUI.Borders.empty(0, 4)
        }

        private val cell = JPanel(BorderLayout()).apply {
            isOpaque = true
            add(dotPlaceholder, BorderLayout.WEST)
            add(textLabel, BorderLayout.CENTER)
            add(retryButton, BorderLayout.EAST)
        }

        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val bg = if (isSelected) table.selectionBackground else table.background
            val fg = if (isSelected) table.selectionForeground else table.foreground

            cell.background = bg
            textLabel.background = bg
            cell.border = CELL_PADDING
            textLabel.toolTipText = null
            retryButton.isVisible = false
            dotPlaceholder.showDot = false

            if (value is AnalyzerStatus) {
                textLabel.text = value.label
                textLabel.toolTipText = value.tooltip

                textLabel.foreground = if (isSelected) fg else when (value) {
                    AnalyzerStatus.ACTIVE -> COLOR_GREEN
                    AnalyzerStatus.SYNCED -> COLOR_BLUE
                    AnalyzerStatus.FAILED -> COLOR_RED
                    AnalyzerStatus.PREMIUM, AnalyzerStatus.UNSUPPORTED -> COLOR_DIMMED
                    else -> fg
                }

                dotPlaceholder.showDot = value == AnalyzerStatus.ACTIVE
                retryButton.isVisible = value == AnalyzerStatus.FAILED
            }

            return cell
        }
    }

    private class SourceCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            if (value is AnalyzerSource) {
                label.icon = when (value) {
                    AnalyzerSource.SONARQUBE_SERVER -> SonarLintIcons.ICON_SONARQUBE_SERVER_16
                    AnalyzerSource.SONARQUBE_CLOUD -> SonarLintIcons.ICON_SONARQUBE_CLOUD_16
                    AnalyzerSource.LOCAL -> null
                }
                label.text = value.label
                // SQS / SQC sources are highlighted in blue; LOCAL uses the default foreground
                if (!isSelected && value != AnalyzerSource.LOCAL) {
                    label.foreground = COLOR_BLUE
                }
            }
            label.horizontalAlignment = SwingConstants.CENTER
            label.border = CELL_PADDING
            return label
        }
    }

    /**
     * Renders the VERSION column.
     *
     * - No version (null): em-dash in dimmed colour
     * - Server overrides local version: blue text + tooltip "Overriding local version X.Y.Z"
     * - Same version: normal label foreground
     */
    private class VersionCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            label.horizontalAlignment = CENTER
            label.toolTipText = null
            label.border = CELL_PADDING

            if (value is SupportedLanguageRow) {
                val version = value.version
                if (version == null) {
                    label.text = "\u2013"
                    if (!isSelected) label.foreground = COLOR_DIMMED
                } else if (!isSelected && value.isVersionOverriddenByServer) {
                    label.text = version
                    label.foreground = COLOR_BLUE
                    label.toolTipText = "Overriding local version ${value.localVersion}"
                } else {
                    label.text = version
                    if (!isSelected) label.foreground = UIUtil.getLabelForeground()
                }
            }

            return label
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

    // -------------------------------------------------------------------------
    // Mocked data
    // -------------------------------------------------------------------------

    companion object {
        fun buildMockedRows(isBound: Boolean): List<SupportedLanguageRow> {
            val standaloneRows = listOf(
                SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1", localVersion = "7.30.1"),
                SupportedLanguageRow(Language.JS, "JavaScript", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "10.22.0", localVersion = "10.22.0"),
                SupportedLanguageRow(Language.TS, "TypeScript", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "10.22.0", localVersion = "10.22.0"),
                SupportedLanguageRow(Language.PYTHON, "Python", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "4.23.0", localVersion = "4.23.0"),
                SupportedLanguageRow(Language.KOTLIN, "Kotlin", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "2.21.0", localVersion = "2.21.0"),
                SupportedLanguageRow(Language.PHP, "PHP", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "3.38.0", localVersion = "3.38.0"),
                SupportedLanguageRow(Language.XML, "XML", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "2.10.0", localVersion = "2.10.0"),
                SupportedLanguageRow(Language.HTML, "HTML", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "3.13.0", localVersion = "3.13.0"),
                SupportedLanguageRow(Language.CSS, "CSS", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "10.22.0", localVersion = "10.22.0"),
                SupportedLanguageRow(Language.SECRETS, "Secrets", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "10.12.0", localVersion = "10.12.0"),
                SupportedLanguageRow(Language.GO, "Go", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "1.15.0", localVersion = "1.15.0"),
                SupportedLanguageRow(Language.CLOUDFORMATION, "CloudFormation", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "1.41.0", localVersion = "1.41.0"),
                SupportedLanguageRow(Language.DOCKER, "Docker", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "1.41.0", localVersion = "1.41.0"),
                SupportedLanguageRow(Language.KUBERNETES, "Kubernetes", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "1.41.0", localVersion = "1.41.0"),
                SupportedLanguageRow(Language.TERRAFORM, "Terraform", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "1.41.0", localVersion = "1.41.0"),
                SupportedLanguageRow(Language.YAML, "YAML", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "1.41.0", localVersion = "1.41.0"),
                SupportedLanguageRow(Language.RUBY, "Ruby", AnalyzerStatus.FAILED, AnalyzerSource.LOCAL, "1.15.0", localVersion = "1.15.0"),
                SupportedLanguageRow(Language.SCALA, "Scala", AnalyzerStatus.PREMIUM, AnalyzerSource.LOCAL, null),
                SupportedLanguageRow(Language.SWIFT, "Swift", AnalyzerStatus.PREMIUM, AnalyzerSource.LOCAL, null),
                SupportedLanguageRow(Language.PLSQL, "PL/SQL", AnalyzerStatus.UNSUPPORTED, AnalyzerSource.LOCAL, null),
            )

            if (!isBound) return standaloneRows

            return standaloneRows.map { row ->
                when (row.status) {
                    AnalyzerStatus.PREMIUM -> row.copy(
                        status = AnalyzerStatus.SYNCED,
                        source = AnalyzerSource.SONARQUBE_SERVER,
                        version = "1.2.41",
                    )
                    // Java demonstrates a server override: server pushes a newer version
                    AnalyzerStatus.ACTIVE -> if (row.language == Language.JAVA) {
                        row.copy(
                            status = AnalyzerStatus.SYNCED,
                            source = AnalyzerSource.SONARQUBE_SERVER,
                            version = "7.31.0",
                        )
                    } else {
                        row
                    }
                    else -> row
                }
            }
        }
    }
}

private typealias Language = org.sonarsource.sonarlint.core.rpc.protocol.common.Language
