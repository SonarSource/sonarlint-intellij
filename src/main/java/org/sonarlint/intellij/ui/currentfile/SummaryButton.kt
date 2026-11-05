/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.currentfile

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleAction.CLICK
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleContext.ACCESSIBLE_NAME_PROPERTY
import javax.accessibility.AccessibleContext.ACCESSIBLE_STATE_PROPERTY
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleRole.TOGGLE_BUTTON
import javax.accessibility.AccessibleState.CHECKED
import javax.accessibility.AccessibleStateSet
import javax.swing.AbstractAction
import javax.swing.KeyStroke
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor

data class SummaryUiModel(
    val icon: Icon? = null,
    val backgroundColor: JBColor? = null,
    val borderColor: JBColor? = null
)

private const val DISABLED_TOOLTIP = "Requires connected mode: Bind your project to SonarQube (Server, Cloud) to enable this feature."

class SummaryButton(
    private val typeNameSingular: String,
    private val typeNamePlural: String,
    listener: (Boolean) -> Unit,
    tooltipText: String
) : RoundedPanelWithBackgroundColor() {

    private var count = 0
    private var iconLabel = JBLabel()
    private val textLabel = JBLabel()
    private var isSelected = false
    private var selectionListener: ((Boolean) -> Unit)? = null
    private var defaultTooltip: String = tooltipText
    private var disabledTooltip: String? = null
    private var alpha: Float = 1.0f
    private var isHovered = false
    private var spacePressed = false
    private var lastBorderColor: JBColor? = null
    private var lastBackgroundColor: JBColor? = null

    init {
        isFocusable = true
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed SPACE"), "press")
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released SPACE"), "toggle")
        actionMap.put("press", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                spacePressed = isEnabled
            }
        })
        actionMap.put("toggle", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (spacePressed) activate()
                spacePressed = false
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) = repaint()
            override fun focusLost(e: FocusEvent?) {
                spacePressed = false
                repaint()
            }
        })
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        iconLabel.verticalAlignment = SwingConstants.CENTER
        textLabel.verticalAlignment = SwingConstants.CENTER
        iconLabel.horizontalAlignment = SwingConstants.CENTER
        textLabel.horizontalAlignment = SwingConstants.CENTER
        border = JBUI.Borders.empty(0, 8)
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        textLabel.text = formatText()
        selectionListener = listener
        add(Box.createRigidArea(Dimension(4, 0)))
        add(iconLabel)
        add(Box.createRigidArea(Dimension(4, 0)))
        add(textLabel)
        add(Box.createRigidArea(Dimension(6, 0)))
        toolTipText = tooltipText
        isOpaque = false
        disabledTooltip = DISABLED_TOOLTIP
        updateSelection()
        updateBorderColor(JBColor.GRAY) // Always start with a grey border

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isEnabled) return
                if (SwingUtilities.isLeftMouseButton(e)) {
                    requestFocusInWindow()
                    activate()
                }
            }
            override fun mouseEntered(e: MouseEvent) {
                if (isEnabled) {
                    isHovered = true
                    updateColors(lastBackgroundColor, lastBorderColor)
                }
            }
            override fun mouseExited(e: MouseEvent) {
                if (isEnabled) {
                    isHovered = false
                    updateColors(lastBackgroundColor, lastBorderColor)
                }
            }
        })
    }

    fun update(count: Int, uiModel: SummaryUiModel) {
        val oldName = getAccessibleContext().accessibleName
        this.count = count
        this.iconLabel.icon = uiModel.icon
        this.textLabel.text = formatText()
        getAccessibleContext().firePropertyChange(ACCESSIBLE_NAME_PROPERTY, oldName, formatText())
        lastBackgroundColor = uiModel.backgroundColor
        lastBorderColor = uiModel.borderColor
        updateColors(uiModel.backgroundColor, uiModel.borderColor)
    }

    private fun formatText(): String {
        if (count == 0) {
            // When disabled (not supported), just show the type name without "No"
            // When enabled but count is 0, show "No [type]" to indicate zero findings
            return if (isEnabled) "No $typeNamePlural" else typeNamePlural
        }
        if (count == 1) {
            return "1 $typeNameSingular"
        }
        return "$count $typeNamePlural"
    }

    fun setSelected(selected: Boolean) {
        if (this.isSelected != selected) {
            val oldState = if (isSelected) CHECKED else null
            this.isSelected = selected
            getAccessibleContext().firePropertyChange(ACCESSIBLE_STATE_PROPERTY, oldState, if (selected) CHECKED else null)
            updateSelection()
            updateColors(lastBackgroundColor, lastBorderColor)
            selectionListener?.invoke(isSelected)
        }
    }

    fun isSelected(): Boolean = isSelected

    private fun activate(): Boolean {
        if (!isEnabled) return false
        setSelected(!isSelected)
        return true
    }

    override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) accessibleContext = AccessibleSummaryButton()
        return accessibleContext
    }

    protected inner class AccessibleSummaryButton : AccessibleJPanel(), AccessibleAction {
        override fun getAccessibleRole(): AccessibleRole = TOGGLE_BUTTON
        override fun getAccessibleName(): String = super.getAccessibleName() ?: formatText()
        override fun getAccessibleStateSet(): AccessibleStateSet = super.getAccessibleStateSet().apply {
            if (isSelected) add(CHECKED)
        }
        override fun getAccessibleAction(): AccessibleAction = this
        override fun getAccessibleActionCount(): Int = 1
        override fun getAccessibleActionDescription(index: Int): String? = if (index == 0) CLICK else null
        override fun doAccessibleAction(index: Int): Boolean = index == 0 && activate()
    }

    private fun updateColors(newBackgroundColor: JBColor?, newBorderColor: JBColor?) {
        // Hover effect: slightly change background or border color if hovered and enabled
        val bg = when {
            isSelected && isHovered && isEnabled -> UIUtil.getPanelBackground().brighter().brighter()
            isSelected -> UIUtil.getPanelBackground().brighter()
            isHovered && isEnabled -> UIUtil.getPanelBackground().darker()
            else -> newBackgroundColor
        }
        updateBackgroundColor(bg)
        // If disabled, always use grey border
        val border = when {
            !isEnabled -> JBColor.GRAY
            isSelected -> JBColor.GRAY.darker()
            isHovered && isEnabled && newBorderColor != null -> newBorderColor.darker()
            else -> newBorderColor ?: JBColor.GRAY
        }
        updateBorderColor(border)
    }

    private fun updateSelection() {
        textLabel.foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
        font = UIUtil.getLabelFont().deriveFont(if (isSelected) Font.BOLD else Font.PLAIN)
        repaint()
    }

    override fun setEnabled(enabled: Boolean) {
        val oldName = formatText()
        super.setEnabled(enabled)
        if (!enabled) spacePressed = false
        isFocusable = enabled
        cursor = if (enabled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
        toolTipText = if (enabled) defaultTooltip else (disabledTooltip ?: defaultTooltip)
        iconLabel.isEnabled = enabled
        textLabel.isEnabled = enabled
        textLabel.text = formatText()
        getAccessibleContext().firePropertyChange(ACCESSIBLE_NAME_PROPERTY, oldName, formatText())
        alpha = if (enabled) 1.0f else 0.5f
        // When disabled, force border to grey
        if (!enabled) {
            updateBorderColor(JBColor.GRAY)
        }
        repaint()
    }

    fun setTooltipText(tooltip: String) {
        this.disabledTooltip = tooltip
        if (!isEnabled) {
            toolTipText = tooltip
        }
    }

    override fun paintComponent(g: Graphics) {
        if (alpha < 1.0f) {
            val g2 = g.create() as Graphics2D
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            super.paintComponent(g2)
            g2.dispose()
        } else {
            super.paintComponent(g)
        }
        if (hasFocus()) {
            val focusGraphics = g.create()
            try {
                focusGraphics.color = JBColor.namedColor("Component.focusColor", JBColor.BLUE)
                focusGraphics.drawRoundRect(2, 2, width - 5, height - 5, 16, 16)
            } finally {
                focusGraphics.dispose()
            }
        }
    }
}
