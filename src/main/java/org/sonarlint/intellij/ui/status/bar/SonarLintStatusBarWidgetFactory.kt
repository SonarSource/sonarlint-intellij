package org.sonarlint.intellij.ui.status.bar

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.ui.components.JBLabel
import javax.swing.JPanel

class SonarLintStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "SonarLint"

    override fun getDisplayName() = "SonarLint"

    override fun isAvailable(project: Project) = true

    override fun createWidget(project: Project): SonarLintStatusBarWidget {
        return SonarLintStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // nothing to do
    }

    override fun canBeEnabledOn(statusBar: StatusBar) = true
}

class SonarLintStatusBarWidget(project: Project) : EditorBasedStatusBarPopup(project, false) {
    override fun ID() = "SonarLintWidget"

    override fun getWidgetState(file: VirtualFile?): WidgetState = WidgetState.NO_CHANGE_MAKE_VISIBLE

    override fun createPopup(context: DataContext?): ListPopup? {
        val factory = JBPopupFactory.getInstance()
        val group = DefaultActionGroup()
        return null
    }

    override fun createInstance(project: Project): StatusBarWidget {
        return this
    }

    override fun createComponent(): JPanel {
        val jPanel = JPanel()
        jPanel.add(JBLabel("SonarLint focus: new code"))
        return jPanel
    }

}
