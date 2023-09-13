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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.finding.persistence.FindingsCache
import javax.swing.JComponent

class SonarLintTrafficLightAction(private val editor: Editor) : AnAction(), CustomComponentAction {

    companion object {
        private val FINDINGS_NUMBER = Key<Int>("FINDINGS_NUMBER")
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return SonarLintTrafficLightWidget(this, presentation, place, editor)
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        presentation.getClientProperty(FINDINGS_NUMBER)?.let { (component as SonarLintTrafficLightWidget).refresh(it) }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { file ->
            val presentation = e.presentation
            val findingsNumber = SonarLintUtils.getService(project, FindingsCache::class.java).getFindingsForFile(file).filter { !it.isResolved }.size
            presentation.putClientProperty(FINDINGS_NUMBER, findingsNumber)
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        editor.project?.let { SonarLintUtils.getService(it, SonarLintToolWindow::class.java).openOrCloseCurrentFileTab() }
    }

}
