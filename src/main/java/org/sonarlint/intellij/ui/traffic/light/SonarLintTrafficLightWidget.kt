package org.sonarlint.intellij.ui.traffic.light

import com.intellij.openapi.actionSystem.ActionButtonComponent.NORMAL
import com.intellij.openapi.actionSystem.ActionButtonComponent.POPPED
import com.intellij.openapi.actionSystem.ActionButtonComponent.PUSHED
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.persistence.FindingsCache
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel

class SonarLintTrafficLightWidget(action: AnAction, private val editor: Editor) : JPanel() {
    private val dashboardPopup = SonarLintDashboardPopup(editor)
    private val iconAndFindingsCountLabel = JLabel(SonarLintIcons.SONARLINT_GREEN)
    private var mousePressed = false
    private var mouseHover = false

    private val jLabel: JLabel
        get() {
            val iconAndFindingsCountLabel = JLabel(SonarLintIcons.SONARLINT_GREEN)
            return iconAndFindingsCountLabel
        }

    init {
        isOpaque = false
        add(iconAndFindingsCountLabel)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                mousePressed = true
                repaint()
            }

            override fun mouseReleased(e: MouseEvent?) {
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
    }

    override fun paintComponent(graphics: Graphics) {
        val state = if (mousePressed) PUSHED else if (mouseHover) POPPED else NORMAL
        if (state == NORMAL) return
        val rect = Rectangle(size)
        JBInsets.removeFrom(rect, insets)

        val color =
            if (state == PUSHED) JBUI.CurrentTheme.ActionButton.pressedBackground() else JBUI.CurrentTheme.ActionButton.hoverBackground()

        ActionButtonLook.SYSTEM_LOOK.paintLookBackground(graphics, rect, color)
    }

    fun refresh() {
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val findings = getService(project, FindingsCache::class.java).getFindingsForFile(file)
        if (findings.isEmpty()) {
            iconAndFindingsCountLabel.icon = SonarLintIcons.SONARLINT_GREEN
        } else {
            iconAndFindingsCountLabel.icon = SonarLintIcons.SONARLINT
            iconAndFindingsCountLabel.text = findings.size.toString()
        }
    }

}
