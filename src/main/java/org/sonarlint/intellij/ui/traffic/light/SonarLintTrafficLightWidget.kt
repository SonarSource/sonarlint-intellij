/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.ui.traffic.light

import com.intellij.openapi.actionSystem.ActionButtonComponent.NORMAL
import com.intellij.openapi.actionSystem.ActionButtonComponent.POPPED
import com.intellij.openapi.actionSystem.ActionButtonComponent.PUSHED
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.persistence.FindingsCache
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border
import javax.swing.plaf.FontUIResource

class SonarLintTrafficLightWidget(
    private val action: AnAction,
    private val presentation: Presentation,
    private val place: String,
    private val editor: Editor,
) : JPanel() {

    private val dashboardPopup = SonarLintDashboardPopup(editor)
    private var iconAndFindingsCountLabel = JLabel()
    private var mousePressed = false
    private var mouseHover = false

    init {
        isOpaque = false

        if (!SystemInfo.isWindows) {
            iconAndFindingsCountLabel.font = FontUIResource(font.deriveFont(font.style, (font.size - JBUIScale.scale(2)).toFloat()))
        }
        iconAndFindingsCountLabel.foreground = JBColor {
            ObjectUtils.notNull(
                editor.colorsScheme.getColor(ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())),
                ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground()).defaultColor
            )
        }
        add(iconAndFindingsCountLabel)

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                mousePressed = true
                repaint()
            }

            override fun mouseReleased(e: MouseEvent?) {
                val context = ActionToolbar.getDataContextFor(this@SonarLintTrafficLightWidget)
                val event = AnActionEvent.createFromInputEvent(e, place, presentation, context, false, true)
                ActionUtil.performActionDumbAwareWithCallbacks(action, event)
                mousePressed = false
                repaint()
            }

            override fun mouseEntered(e: MouseEvent) {
                mouseHover = true
                repaint()
                dashboardPopup.scheduleShow(this@SonarLintTrafficLightWidget)
            }

            override fun mouseExited(e: MouseEvent) {
                mouseHover = false
                repaint()
                dashboardPopup.scheduleHide()
            }
        })

        border = object : Border {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, w: Int, h: Int) {
                // Empty borders
            }

            override fun isBorderOpaque(): Boolean {
                return false
            }

            override fun getBorderInsets(c: Component): Insets {
                return JBUI.insets(0, 2)
            }
        }
    }

    override fun paintComponent(graphics: Graphics) {
        val state = if (mousePressed) PUSHED else if (mouseHover) POPPED else NORMAL
        if (state == NORMAL) return

        val rect = Rectangle(size)
        JBInsets.removeFrom(rect, JBUI.insets(2))

        val color =
            if (state == PUSHED) JBUI.CurrentTheme.ActionButton.pressedBackground()
            else JBUI.CurrentTheme.ActionButton.hoverBackground()

        ActionButtonLook.SYSTEM_LOOK.paintLookBackground(graphics, rect, color)
    }

    fun refresh() {
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val findings = getService(project, FindingsCache::class.java).getFindingsForFile(file).filter { !it.isResolved }
        if (findings.isEmpty()) {
            iconAndFindingsCountLabel.icon = SonarLintIcons.SONARLINT_ACTION_GREEN_12PX
        } else {
            iconAndFindingsCountLabel.icon = SonarLintIcons.SONARLINT_ACTION_12PX
            iconAndFindingsCountLabel.text = findings.size.toString()
        }
    }

}
