package org.sonarlint.intellij.ui.currentfile

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JSeparator
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

val STANDARD_SEVERITIES = arrayOf("All", "Blocker", "Critical", "Major", "Minor", "Info")
val MQR_IMPACTS = arrayOf("All", "Blocker", "High", "Medium", "Low", "Info")
val RESOLVED_STATUS = arrayOf("All", "Open", "Resolved")
val SORTING_MODES = arrayOf("Date", "Impact", "Rule key", "Line number")

class FiltersPanel(
    private val onFilterChanged: () -> Unit,
    private val onSortingChanged: (SortMode) -> Unit
) : JBPanel<FiltersPanel>() {

    val searchLabel = JBLabel("Search:")
    val searchField = SearchTextField()
    val severityLabel = JBLabel("Severity:")
    val severityCombo = ComboBox(STANDARD_SEVERITIES)
    val statusLabel = JBLabel("Status:")
    val statusCombo = ComboBox(RESOLVED_STATUS)
    val quickFixLabel = JBLabel("With fix suggestion:")
    val quickFixCheckBox = JBCheckBox()
    val sortLabel = JBLabel("Sort by:")
    val sortCombo = ComboBox(SORTING_MODES)
    val cleanFiltersBtn = JButton("Clear")

    var filterText = ""
    var filterSeverity = "All"
    var filterStatus = "All"
    private var sortMode = SortMode.DATE

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = JBUI.Borders.empty(0, 8, 8, 8)
        isVisible = false

        initComponents()
        addComponents()
    }

    private fun initComponents() {
        searchLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        searchField.apply {
            toolTipText = "Search by rule name, message or file"
            textEditor.columns = 12
            maximumSize = Dimension(160, 30)
            preferredSize = Dimension(120, 30)
            addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    filterText = searchField.text
                    onFilterChanged()
                }
                override fun removeUpdate(e: DocumentEvent) {
                    filterText = searchField.text
                    onFilterChanged()
                }
                override fun changedUpdate(e: DocumentEvent) {
                    filterText = searchField.text
                    onFilterChanged()
                }

            })
        }

        severityLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        severityCombo.apply {
            toolTipText = "Filter by severity"
            maximumSize = Dimension(90, 30)
            addActionListener { _ ->
                filterSeverity = severityCombo.selectedItem as String
                onFilterChanged()
            }
        }

        statusLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        statusCombo.apply {
            toolTipText = "Filer by status"
            maximumSize = Dimension(90, 30)
            addActionListener { _ ->
                filterStatus = statusCombo.selectedItem as String
                onFilterChanged()
            }
        }

        quickFixLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        quickFixCheckBox.apply {
            maximumSize = Dimension(24, 30)
            addActionListener { _ ->
                onFilterChanged()
            }
        }

        sortLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        sortCombo.apply {
            toolTipText = "Sort findings by"
            maximumSize = Dimension(160, 30)
            addActionListener { _ ->
                val selected = sortCombo.selectedItem as String
                sortMode = when (selected) {
                    "Impact" -> SortMode.IMPACT
                    "Date" -> SortMode.DATE
                    "Rule key" -> SortMode.RULE_KEY
                    else -> SortMode.LINE
                }
                onSortingChanged(sortMode)
                onFilterChanged()
            }
        }

        cleanFiltersBtn.apply {
            toolTipText = "Reset all filters"
            maximumSize = Dimension(60, 30)
            addActionListener { _ ->
                filterText = ""
                searchField.text = ""
                filterSeverity = "All"
                severityCombo.selectedItem = "All"
                filterStatus = "All"
                statusCombo.selectedItem = "All"
                quickFixCheckBox.isSelected = false
                sortMode = SortMode.DATE
                sortCombo.selectedItem = "Date"
                onFilterChanged()
            }
        }
    }

    private fun addComponents() {
        add(searchLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(searchField)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(severityLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(severityCombo)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(quickFixLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(quickFixCheckBox)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(sortLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(sortCombo)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(JSeparator(JSeparator.VERTICAL).apply {
            maximumSize = Dimension(8, 24)
        })
        add(Box.createRigidArea(Dimension(4, 0)))
        add(cleanFiltersBtn)
    }

}
