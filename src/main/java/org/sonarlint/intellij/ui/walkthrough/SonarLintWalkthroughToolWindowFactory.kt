/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.ui.walkthrough

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import org.sonarlint.intellij.common.util.SonarLintUtils.getService

private const val WALKTHROUGH_SONARQUBE_FOR_IDE: String = "Welcome to SonarQube for IDE"

class SonarLintWalkthroughToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setType(ToolWindowType.DOCKED, null)
        val content = toolWindow.contentManager.factory.createContent(WalkthroughPanel(project), "", false)
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.addContentManagerListener(getService(project, SonarLintWalkthroughToolWindow::class.java))
    }

}

fun getSonarLintToolWindow(project: Project): ToolWindow? {
    val toolWindowManager: ToolWindowManager = ToolWindowManager.getInstance(project)
    return toolWindowManager.getToolWindow(WALKTHROUGH_SONARQUBE_FOR_IDE)
}
