package org.sonarlint.intellij.ui.traffic.light

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.editor.Editor
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import javax.swing.JComponent

class SonarLintTrafficLightAction(private val editor: Editor) : AnAction(), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return SonarLintTrafficLightWidget(this, editor)
    }

    override fun update(e: AnActionEvent) {
        val component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) as SonarLintTrafficLightWidget?
        component?.refresh()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        getService(project, SonarLintToolWindow::class.java).openCurrentFileTab()
    }
}
